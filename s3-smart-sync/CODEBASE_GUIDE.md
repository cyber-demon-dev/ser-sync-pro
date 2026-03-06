# S3 Smart Sync - Codebase Guide

Technical reference for developers working on this codebase.

## Overview

Python CLI tool for syncing local volumes to Amazon S3 with **smart rename detection**. Uses inode tracking to detect file/folder renames and performs server-side moves (zero upload bandwidth).

## Directory Structure

```text
s3-smart-sync/
├── main.py              # CLI entry point (7 commands)
├── config.py            # Config file parser for batch mode
├── scanner.py           # Filesystem scanner with inode capture
├── db.py                # SQLite database for state tracking
├── s3_client.py         # AWS S3 operations via boto3
├── diff_engine.py       # Sync plan computation
├── archive_handler.py   # Glacier/Deep Archive restore tracking
├── logger.py            # Timestamped logging to logs/ folder
├── requirements.txt     # Python dependencies (boto3)
└── README.md            # User documentation
```

## Module Reference

### main.py

CLI entry point with seven commands:

| Command | Purpose |
|---------|---------|
| `sync` | Full sync with move/upload/delete operations |
| `moves` | **Rename detection only** (no uploads/deletes) |
| `batch` | Run moves + aws s3 sync for all config targets |
| `init` | Create sample config file |
| `status` | Show pending changes without syncing |
| `restore` | Initiate restore for archived S3 objects |
| `restore-status` | Check restore progress |

### config.py

Parses pipe-delimited config file for batch mode.

| Class/Function | Purpose |
|----------------|---------|
| `SyncTarget` | Dataclass: local_path, bucket, prefix, excludes |
| `parse_config()` | Read config file, return list of SyncTarget |
| `create_sample_config()` | Generate template config file |

**Config format:**

```properties
local_path | bucket | prefix | excludes (comma-separated)
```

### scanner.py

Walks local filesystem and captures metadata including inodes.

| Class | Purpose |
|-------|---------|
| `ScannedFile` | Dataclass: path, abs_path, inode, size, mtime |
| `Scanner` | Recursive filesystem walker |

### db.py

SQLite persistence for tracking file state between syncs.

**Schema:**

```sql
CREATE TABLE files (
    path TEXT PRIMARY KEY,
    inode INTEGER NOT NULL,
    size INTEGER NOT NULL,
    mtime REAL NOT NULL,
    synced INTEGER DEFAULT 0
);
CREATE INDEX idx_inode ON files(inode);
```

**Database location:** `.s3-smart-sync.db` in each synced source directory.

### s3_client.py

Boto3 wrapper for S3 operations.

| Method | Purpose |
|--------|---------|
| `upload_file()` | Upload local file to S3 |
| `delete_object()` | Delete single object |
| `delete_objects()` | Batch delete (up to 1000) |
| `move_object()` | **Server-side copy+delete** (detects archived objects) |
| `list_objects()` | List bucket contents |
| `restore_object()` | Initiate Glacier/Deep Archive restore |
| `get_restore_status()` | Check restore progress |

### diff_engine.py

Computes sync plan by comparing current vs previous state.

**Logic:**

1. Match inodes between current scan and database
2. Same inode + different path → **Move**
3. New inode → **Upload**
4. Missing from local (with `--delete`) → **Delete**

### archive_handler.py

Tracks S3 objects that need Glacier/Deep Archive restoration.

| Method | Purpose |
|--------|---------|
| `log_archived_key()` | Record key needing restore |
| `save_log()` | Write pending keys to `archive_restore_needed.log` |
| `load_pending_keys()` | Read log for restore command |
| `clear_log()` | Remove log after successful restore |

### logger.py

Handles timestamped logging to `logs/` folder.

| Method | Purpose |
|--------|---------|
| `start()` | Create logs folder, open timestamped log file |
| `log()` | Write to file and console |
| `close()` | Finalize and close log file |

## Execution Flow

### Batch Command Flow

```text
batch command
    │
    ├─ parse_config() → List[SyncTarget]
    │
    └─ For each target:
        ├─ cmd_moves()
        │   ├─ Load previous state from SQLite
        │   ├─ Scan current local files
        │   ├─ List S3 objects
        │   ├─ DiffEngine.compute_plan()
        │   ├─ Execute server-side moves only
        │   └─ Update database
        │
        └─ subprocess.run(['aws', 's3', 'sync', ...])
            └─ Handles uploads + deletes with excludes
```

### Moves Command Flow

```text
1. Load previous state from .s3-smart-sync.db
2. Scan local files → Dict[inode, ScannedFile]
3. List S3 objects → Dict[key, S3Object]
4. DiffEngine computes:
   - For each current file inode:
     - If inode in previous AND path differs → queue MOVE
     - BUT only if old path exists in S3 (verified via list)
5. Execute moves: s3.copy_object() + s3.delete_object()
6. Update database with current state
```

## Configuration

**AWS credentials** are read from standard locations:

- `~/.aws/credentials`
- Environment variables (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`)

**Batch config** location: `~/.s3-smart-sync.conf`

## Dependencies

- Python 3.9+
- boto3
- AWS CLI (for batch mode's aws s3 sync calls)
