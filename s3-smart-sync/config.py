"""
S3 Smart Sync - Configuration Parser

Reads a pipe-delimited config file defining multiple volume -> bucket sync targets.

Config format:
    local_path | bucket | prefix (optional) | excludes (comma-separated, optional)

Example:
    ~/music/.                | bucket-culprit-music          |        | *.m4p, .*, */.*
    /Volumes/Storage/.       | bucket-culprit-crates-storage |        | *.m4p, .*, */.*
"""

import os
from pathlib import Path
from dataclasses import dataclass, field
from typing import List, Optional


# Default config file location
DEFAULT_CONFIG_PATH = os.path.expanduser("~/.s3-smart-sync.conf")


@dataclass
class SyncTarget:
    """Represents a single volume -> bucket sync target."""
    local_path: str          # Absolute path to local directory
    bucket: str              # S3 bucket name
    prefix: str = ""         # Optional S3 prefix (folder)
    excludes: List[str] = field(default_factory=list)  # Exclude patterns
    
    def get_s3_uri(self) -> str:
        """Get the full S3 URI for this target."""
        if self.prefix:
            return f"s3://{self.bucket}/{self.prefix}"
        return f"s3://{self.bucket}"
    
    def get_aws_sync_command(self, delete: bool = True) -> List[str]:
        """
        Generate the aws s3 sync command for this target.
        
        Returns:
            List of command arguments for subprocess.
        """
        cmd = ["aws", "s3", "sync", self.local_path, self.get_s3_uri()]
        
        if delete:
            cmd.append("--delete")
        
        for exclude in self.excludes:
            cmd.extend(["--exclude", exclude])
        
        return cmd


def parse_config(config_path: Optional[str] = None) -> List[SyncTarget]:
    """
    Parse config file and return list of SyncTarget objects.
    
    Args:
        config_path: Path to config file. If None, uses default location.
        
    Returns:
        List of SyncTarget objects.
        
    Raises:
        FileNotFoundError: If config file doesn't exist.
        ValueError: If config file has invalid format.
    """
    path = config_path or DEFAULT_CONFIG_PATH
    
    if not os.path.exists(path):
        raise FileNotFoundError(f"Config file not found: {path}")
    
    targets = []
    
    with open(path, 'r') as f:
        for line_num, line in enumerate(f, 1):
            # Skip empty lines and comments
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            
            # Parse pipe-delimited fields
            parts = [p.strip() for p in line.split('|')]
            
            if len(parts) < 2:
                raise ValueError(
                    f"Invalid config line {line_num}: expected at least 'local_path | bucket'"
                )
            
            local_path = os.path.expanduser(parts[0])
            bucket = parts[1]
            prefix = parts[2] if len(parts) > 2 else ""
            
            # Parse excludes (comma-separated)
            excludes = []
            if len(parts) > 3 and parts[3]:
                excludes = [e.strip() for e in parts[3].split(',')]
            
            # Validate local path exists
            if not os.path.isdir(local_path):
                print(f"  [WARNING] Local path does not exist: {local_path}")
            
            targets.append(SyncTarget(
                local_path=local_path,
                bucket=bucket,
                prefix=prefix,
                excludes=excludes
            ))
    
    return targets


def create_sample_config(config_path: Optional[str] = None) -> str:
    """
    Create a sample config file with commented examples.
    
    Args:
        config_path: Path to create config. If None, uses default location.
        
    Returns:
        Path to created config file.
    """
    path = config_path or DEFAULT_CONFIG_PATH
    
    sample_content = """# S3 Smart Sync Configuration
# ============================
# Format: local_path | bucket | prefix (optional) | excludes (comma-separated)
#
# Examples:
# ~/music/.                | my-music-bucket         |              | *.m4p, .*, */.*
# /Volumes/Backup/.        | my-backup-bucket        | backups/mac  | .DS_Store, *.tmp
#
# Notes:
# - Local paths are expanded (~ becomes home directory)
# - Prefix is optional (leave empty column if not needed)
# - Multiple excludes are comma-separated
# - Lines starting with # are comments

# Add your sync targets below:
"""
    
    with open(path, 'w') as f:
        f.write(sample_content)
    
    return path
