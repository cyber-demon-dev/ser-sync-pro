"""
S3 Smart Sync - Diff Engine
Compares local state vs previous state to detect renames, uploads, and deletions.
"""

from typing import Dict, List, Tuple, NamedTuple, Optional
from dataclasses import dataclass
from db import FileRecord
from scanner import ScannedFile


@dataclass
class SyncAction:
    """Represents an action to take during sync."""
    action_type: str  # 'upload', 'delete', 'move'
    path: str
    old_path: Optional[str] = None  # For moves
    local_abs_path: Optional[str] = None  # For uploads


class SyncPlan:
    """Container for all planned sync actions."""
    
    def __init__(self):
        self.moves: List[Tuple[str, str]] = []      # (old_key, new_key)
        self.uploads: List[Tuple[str, str]] = []    # (local_abs_path, key)
        self.deletes: List[str] = []                # keys to delete
    
    def summary(self) -> str:
        """Return a summary of planned actions."""
        return (
            f"Sync Plan: {len(self.moves)} moves, "
            f"{len(self.uploads)} uploads, "
            f"{len(self.deletes)} deletes"
        )


class DiffEngine:
    """
    Compares local filesystem state against previous state and S3 state
    to determine what sync operations are needed.
    """

    def __init__(
        self,
        current_files: Dict[int, ScannedFile],
        previous_records: Dict[int, FileRecord],
        s3_objects: Dict[str, object],
        delete_orphans: bool = False
    ):
        """
        Initialize diff engine.
        
        Args:
            current_files: Current local files indexed by inode.
            previous_records: Previous file records from database, indexed by inode.
            s3_objects: Current S3 objects indexed by key.
            delete_orphans: Whether to delete S3 objects not found locally.
        """
        self.current_files = current_files
        self.previous_records = previous_records
        self.s3_objects = s3_objects
        self.delete_orphans = delete_orphans

    def compute_plan(self) -> SyncPlan:
        """
        Analyze differences and compute sync plan.
        
        Priority order:
        1. Detect renames (inode match but path differs) -> queue moves
        2. Detect new/modified files -> queue uploads
        3. Detect orphans (in S3 but not local) -> queue deletes (if enabled)
        
        Returns:
            SyncPlan with all actions to execute.
        """
        plan = SyncPlan()
        
        # Track which S3 keys are accounted for
        accounted_s3_keys = set()
        
        # Build path -> inode mapping for current files
        current_by_path = {f.path: f for f in self.current_files.values()}
        
        # Step 1: Detect renames and uploads
        for inode, current_file in self.current_files.items():
            previous = self.previous_records.get(inode)
            
            if previous is not None:
                # File existed before (same inode)
                if previous.path != current_file.path:
                    # Path changed -> RENAME (server-side move)
                    # Only if the old path exists in S3
                    if previous.path in self.s3_objects:
                        plan.moves.append((previous.path, current_file.path))
                        accounted_s3_keys.add(previous.path)
                        accounted_s3_keys.add(current_file.path)
                    else:
                        # Old path not in S3, just upload to new path
                        plan.uploads.append((current_file.abs_path, current_file.path))
                        accounted_s3_keys.add(current_file.path)
                elif (
                    current_file.size != previous.size or
                    current_file.mtime > previous.mtime
                ):
                    # File modified -> upload
                    plan.uploads.append((current_file.abs_path, current_file.path))
                    accounted_s3_keys.add(current_file.path)
                else:
                    # File unchanged
                    accounted_s3_keys.add(current_file.path)
            else:
                # New file (inode not seen before)
                if current_file.path not in self.s3_objects:
                    # Not in S3 -> upload
                    plan.uploads.append((current_file.abs_path, current_file.path))
                accounted_s3_keys.add(current_file.path)
        
        # Step 2: Detect orphans (files in S3 but not in current local)
        if self.delete_orphans:
            for s3_key in self.s3_objects:
                if s3_key not in accounted_s3_keys:
                    plan.deletes.append(s3_key)
        
        return plan


def create_diff_engine(
    current_files: Dict[int, ScannedFile],
    previous_records: Dict[int, FileRecord],
    s3_objects: Dict[str, object],
    delete_orphans: bool = False
) -> DiffEngine:
    """Factory function to create a DiffEngine."""
    return DiffEngine(current_files, previous_records, s3_objects, delete_orphans)
