"""
S3 Smart Sync - Archive Handler Module
Handles Glacier/Deep Archive restore operations and logging.
"""

import os
from datetime import datetime
from typing import List, Set


class ArchiveHandler:
    """Manages tracking and restoration of archived S3 objects."""
    
    # Log file for keys that need restoration
    RESTORE_LOG_FILE = "archive_restore_needed.log"
    
    def __init__(self, source_path: str):
        """
        Initialize archive handler.
        
        Args:
            source_path: Base path for storing the restore log.
        """
        self.source_path = source_path
        self.log_path = os.path.join(source_path, self.RESTORE_LOG_FILE)
        self._pending_keys: Set[str] = set()
    
    def log_archived_key(self, key: str, error_msg: str = "") -> None:
        """
        Log a key that requires restoration before operations can proceed.
        
        Args:
            key: S3 object key that needs restoration.
            error_msg: Optional error message for context.
        """
        self._pending_keys.add(key)
        print(f"  [ARCHIVED] {key} - requires restore before operations")
    
    def save_log(self) -> int:
        """
        Save all pending archived keys to the log file.
        
        Returns:
            Number of keys saved.
        """
        if not self._pending_keys:
            return 0
        
        # Append to existing log or create new
        with open(self.log_path, 'a') as f:
            timestamp = datetime.now().isoformat()
            f.write(f"# Logged at {timestamp}\n")
            for key in sorted(self._pending_keys):
                f.write(f"{key}\n")
            f.write("\n")
        
        count = len(self._pending_keys)
        print(f"\nLogged {count} archived object(s) to: {self.log_path}")
        return count
    
    def load_pending_keys(self) -> List[str]:
        """
        Load previously logged keys from the restore log file.
        
        Returns:
            List of keys that need restoration.
        """
        if not os.path.exists(self.log_path):
            return []
        
        keys = []
        with open(self.log_path, 'r') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#'):
                    keys.append(line)
        
        return list(set(keys))  # Remove duplicates
    
    def clear_log(self) -> None:
        """Remove the restore log file after successful restoration."""
        if os.path.exists(self.log_path):
            os.remove(self.log_path)
            print(f"Cleared restore log: {self.log_path}")
    
    def has_pending(self) -> bool:
        """Check if there are any pending archived keys."""
        return len(self._pending_keys) > 0
    
    @property
    def pending_count(self) -> int:
        """Number of keys pending restoration."""
        return len(self._pending_keys)
