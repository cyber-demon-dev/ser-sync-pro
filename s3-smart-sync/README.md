# S3 Smart Sync

A bandwidth-efficient S3 sync tool that detects local file/folder renames and performs **server-side moves** instead of re-uploading.

## Features

- **Smart Rename Detection**: Uses inode tracking to detect when files/folders are renamed locally
- **Server-Side Moves**: Renames trigger S3 copy+delete operations (zero upload bandwidth)
- **Mirror Mode**: Optional `--delete` flag to remove S3 files not found locally
- **Dry Run**: Preview what would happen without making changes

## Requirements

- macOS (comes with Python pre-installed)
- AWS account with S3 bucket
- AWS credentials (Access Key ID + Secret Access Key)

## Installation (First Time Setup)

### Step 1: Verify Python is installed

Open Terminal and run:

```bash
python3 --version
```

You should see something like `Python 3.11.4`. If not, install Python from [python.org](https://www.python.org/downloads/).

### Step 2: Install dependencies

Navigate to the s3-smart-sync folder and install required packages:

```bash
cd /Users/culprit/git/ser-sync-pro/s3-smart-sync
pip3 install -r requirements.txt
```

### Step 3: Configure AWS credentials

Create your AWS credentials file:

```bash
mkdir -p ~/.aws
nano ~/.aws/credentials
```

Paste this (replace with your actual keys):

```ini
[default]
aws_access_key_id = YOUR_ACCESS_KEY_HERE
aws_secret_access_key = YOUR_SECRET_KEY_HERE
```

Save: `Ctrl+O`, `Enter`, `Ctrl+X`

> **Where to get AWS keys?**  
> AWS Console → IAM → Users → Your User → Security credentials → Create access key

## Usage

### Sync a directory to S3

```bash
# Basic sync (won't delete orphaned S3 files)
python main.py sync /path/to/local/folder my-bucket

# With prefix (folder in bucket)
python main.py sync /path/to/local/folder my-bucket --prefix backups/volume1

# Mirror mode (delete S3 files not found locally)
python main.py sync /path/to/local/folder my-bucket --delete

# Dry run (preview changes)
python main.py sync /path/to/local/folder my-bucket --dry-run
```

### Check sync status

```bash
python main.py status /path/to/local/folder
```

### Restore archived objects (Glacier/Deep Archive)

If sync encounters archived objects (Intelligent-Tiering Archive, Glacier, Deep Archive), they are logged automatically.

```bash
# Initiate restore (default: bulk tier, 12-48 hours)
python main.py restore /path/to/local/folder my-bucket

# Use faster tier (more expensive)
python main.py restore /path/to/local/folder my-bucket --tier standard  # 3-5 hours
python main.py restore /path/to/local/folder my-bucket --tier expedited # 1-5 minutes

# Check restore progress
python main.py restore-status /path/to/local/folder my-bucket

# After restore completes, run sync again
python main.py sync /path/to/local/folder my-bucket
```

## How It Works

1. **First Run**: Scans local files and records their inodes in a SQLite database
2. **Subsequent Runs**:
   - Scans local files again
   - Compares inodes: same inode + different path = **rename** (server-side move)
   - New inodes = **upload**
   - Missing inodes (with `--delete`) = **delete from S3**

## Architecture

| File | Purpose |
|------|---------|
| `main.py` | CLI entry point |
| `scanner.py` | Walks local filesystem, captures inodes |
| `db.py` | SQLite for inode tracking |
| `s3_client.py` | boto3 wrapper with move/upload/delete |
| `diff_engine.py` | Computes sync plan from state differences |
| `archive_handler.py` | Tracks archived objects needing restore |
| `logger.py` | Timestamped logging to `logs/` folder |

## Logging

All sync operations are logged to timestamped files in the `logs/` folder:

```
/path/to/source/
├── logs/
│   ├── s3-sync-sync-2026-01-15_09-12-15.log
│   └── s3-sync-restore-2026-01-16_14-30-00.log
├── archive_restore_needed.log  (only if archived files found)
└── .s3-smart-sync.db
```
