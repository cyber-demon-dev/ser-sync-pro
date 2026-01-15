"""
S3 Smart Sync - Logger Module
Handles logging to timestamped files in logs/ directory.
"""

import os
import sys
from datetime import datetime
from typing import Optional


class Logger:
    """Manages logging to console and timestamped log files."""
    
    def __init__(self, source_path: str, command: str = "sync"):
        """
        Initialize logger.
        
        Args:
            source_path: Base path for storing logs.
            command: Command name for log file naming.
        """
        self.source_path = source_path
        self.command = command
        self.log_dir = os.path.join(source_path, "logs")
        self.log_file: Optional[str] = None
        self._file_handle = None
        
    def start(self) -> str:
        """
        Start logging session. Creates logs folder and opens log file.
        
        Returns:
            Path to the log file.
        """
        # Create logs directory if it doesn't exist
        os.makedirs(self.log_dir, exist_ok=True)
        
        # Generate timestamped filename
        timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
        filename = f"s3-sync-{self.command}-{timestamp}.log"
        self.log_file = os.path.join(self.log_dir, filename)
        
        # Open file for writing
        self._file_handle = open(self.log_file, 'w', encoding='utf-8')
        
        # Log header
        self._write_to_file(f"S3 Smart Sync - {self.command.upper()} Log")
        self._write_to_file(f"Started: {datetime.now().isoformat()}")
        self._write_to_file(f"Source: {self.source_path}")
        self._write_to_file("=" * 60)
        self._write_to_file("")
        
        return self.log_file
    
    def log(self, message: str, also_print: bool = True) -> None:
        """
        Log a message to file and optionally to console.
        
        Args:
            message: Message to log.
            also_print: Whether to also print to console (default True).
        """
        if also_print:
            print(message)
        self._write_to_file(message)
    
    def _write_to_file(self, message: str) -> None:
        """Write message to log file if open."""
        if self._file_handle:
            self._file_handle.write(message + "\n")
            self._file_handle.flush()
    
    def close(self) -> None:
        """Close the log file."""
        if self._file_handle:
            self._write_to_file("")
            self._write_to_file("=" * 60)
            self._write_to_file(f"Finished: {datetime.now().isoformat()}")
            self._file_handle.close()
            self._file_handle = None
            print(f"\nLog saved to: {self.log_file}")
    
    def __enter__(self):
        """Context manager entry."""
        self.start()
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        """Context manager exit."""
        if exc_type:
            self.log(f"\nERROR: {exc_type.__name__}: {exc_val}")
        self.close()
        return False


# Global logger instance (set by main.py)
_logger: Optional[Logger] = None


def init_logger(source_path: str, command: str = "sync") -> Logger:
    """Initialize global logger."""
    global _logger
    _logger = Logger(source_path, command)
    return _logger


def get_logger() -> Optional[Logger]:
    """Get global logger instance."""
    return _logger


def log(message: str, also_print: bool = True) -> None:
    """Log a message using global logger."""
    if _logger:
        _logger.log(message, also_print)
    elif also_print:
        print(message)
