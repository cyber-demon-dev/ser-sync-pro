"""
S3 Smart Sync - Local Filesystem Scanner
Scans local directories and captures file metadata including inodes for rename detection.
"""

import os
from typing import Dict, List, Generator
from dataclasses import dataclass
from db import FileRecord


@dataclass
class ScannedFile:
    """Represents a scanned file with its metadata."""
    path: str          # Relative path from source root
    abs_path: str      # Absolute path on disk
    inode: int
    size: int
    mtime: float


class Scanner:
    """Scans local filesystem and builds file index."""

    def __init__(self, source_path: str):
        """
        Initialize scanner with source directory.
        
        Args:
            source_path: Absolute path to the directory to sync.
        """
        self.source_path = os.path.abspath(source_path)
        if not os.path.isdir(self.source_path):
            raise ValueError(f"Source path is not a directory: {self.source_path}")

    def scan(self) -> Generator[ScannedFile, None, None]:
        """
        Walk the source directory and yield ScannedFile objects.
        
        Yields:
            ScannedFile for each file found.
        """
        for root, dirs, files in os.walk(self.source_path):
            # Skip hidden directories
            dirs[:] = [d for d in dirs if not d.startswith('.')]
            
            for filename in files:
                # Skip hidden files
                if filename.startswith('.'):
                    continue
                
                abs_path = os.path.join(root, filename)
                rel_path = os.path.relpath(abs_path, self.source_path)
                
                try:
                    stat = os.stat(abs_path)
                    yield ScannedFile(
                        path=rel_path,
                        abs_path=abs_path,
                        inode=stat.st_ino,
                        size=stat.st_size,
                        mtime=stat.st_mtime
                    )
                except OSError:
                    # File may have been deleted during scan
                    continue

    def scan_to_dict(self) -> Dict[int, ScannedFile]:
        """
        Scan and return results as a dictionary keyed by inode.
        
        Returns:
            Dict mapping inode -> ScannedFile
        """
        return {f.inode: f for f in self.scan()}

    def scan_to_path_dict(self) -> Dict[str, ScannedFile]:
        """
        Scan and return results as a dictionary keyed by relative path.
        
        Returns:
            Dict mapping path -> ScannedFile
        """
        return {f.path: f for f in self.scan()}

    def to_file_records(self) -> List[FileRecord]:
        """
        Scan and convert to FileRecord objects for database storage.
        
        Returns:
            List of FileRecord objects.
        """
        return [
            FileRecord(
                path=f.path,
                inode=f.inode,
                size=f.size,
                mtime=f.mtime,
                synced=False
            )
            for f in self.scan()
        ]
