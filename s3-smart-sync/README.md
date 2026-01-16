# S3 Smart Sync

A bandwidth-efficient S3 sync tool that detects local file/folder renames and performs **server-side moves** instead of re-uploading.

## What This Tool Does

When you rename or move a file locally, normal `aws s3 sync` will:

1. Delete the old file from S3
2. Re-upload the entire file to the new location

**S3 Smart Sync** instead:

1. Detects the rename using filesystem inodes
2. Tells S3 to copy the file to the new location (server-side, no upload)
3. Deletes the old S3 object

This saves bandwidth and time, especially for large files.

---

## Requirements

- macOS (tested on macOS 12+)
- AWS CLI installed and configured
- An existing S3 bucket
- Basic Terminal knowledge

---

## Installation

### Step 1: Open Terminal

Press `Cmd + Space`, type `Terminal`, and press Enter.

### Step 2: Verify Python is installed

```bash
python3 --version
```

You should see something like `Python 3.11.4`. If you get "command not found", install Python from [python.org](https://www.python.org/downloads/).

### Step 3: Verify AWS CLI is installed

```bash
aws --version
```

If not installed, run:

```bash
brew install awscli
```

If you don't have Homebrew, install it first:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

### Step 4: Install Python dependencies

Navigate to the s3-smart-sync folder:

```bash
cd /path/to/s3-smart-sync
pip3 install -r requirements.txt
```

### Step 5: Configure AWS credentials

If you haven't already configured AWS CLI:

```bash
aws configure
```

Enter your Access Key ID, Secret Access Key, and preferred region when prompted.

---

## First-Time Setup (Important!)

Before using batch mode, you need to initialize the tracking database. **Do this once per volume:**

```bash
# Step 1: Make sure S3 matches your local files (standard sync)
aws s3 sync ~/music/. s3://your-bucket-name --delete

# Step 2: Initialize the database (creates .s3-smart-sync.db)
cd /path/to/s3-smart-sync
python3 main.py moves ~/music/. your-bucket-name
```

After this, the database knows about all your files and can detect future renames.

---

## Quick Start (Daily Use)

### Option A: Single Volume

```bash
cd /path/to/s3-smart-sync

# Detect and execute renames, then sync
python3 main.py moves ~/music/. my-bucket
aws s3 sync ~/music/. s3://my-bucket --delete
```

### Option B: Multiple Volumes (Batch Mode)

Create a config file:

```bash
python3 main.py init
```

Edit `~/.s3-smart-sync.conf`:

```properties
# Format: local_path | bucket | prefix | excludes (comma-separated)
~/music/.                | bucket-culprit-music          |        | *.m4p, .*, */.*
/Volumes/Storage/.       | bucket-culprit-crates-storage |        | *.m4p, .*, */.*
/Volumes/Vault/.         | bucket-culprit-crates-vault   |        | *.m4p, .*, */.*
/Volumes/Current/.       | bucket-culprit-crates-current |        | *.m4p, .*, */.*
```

Run all syncs with one command:

```bash
python3 main.py batch
```

---

## Command Reference

| Command | What It Does |
|---------|--------------|
| `python3 main.py batch` | Run moves + aws s3 sync for all targets in config |
| `python3 main.py batch --dry-run` | Preview what would happen |
| `python3 main.py batch --moves-only` | Only detect/execute renames, skip uploads |
| `python3 main.py moves <path> <bucket>` | Rename detection for single volume |
| `python3 main.py init` | Create sample config file |
| `python3 main.py status <path>` | Show pending changes |

---

## How It Works

### The Hybrid Workflow

```text
┌─────────────────────────────────────────────────────────────┐
│  Step 1: Smart Rename Detection (moves command)            │
│  ─────────────────────────────────────────────              │
│  • Scans local files and compares inodes to database        │
│  • Detects renames: same inode + different path             │
│  • Executes server-side S3 moves (copy+delete, no upload)   │
│  • Updates local SQLite database                            │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  Step 2: Standard Sync (aws s3 sync)                        │
│  ─────────────────────────────────────────────              │
│  • Uploads new files                                        │
│  • Uploads modified files                                   │
│  • Deletes orphaned S3 files (with --delete)                │
│  • Applies exclude patterns                                 │
└─────────────────────────────────────────────────────────────┘
```

### Database Location

A hidden SQLite database is created in each synced folder:

```text
/Volumes/Current/
├── .s3-smart-sync.db    ← Tracks file inodes (hidden file)
├── logs/                 ← Sync logs
└── [your files...]
```

> **Note:** If you delete `.s3-smart-sync.db`, the next run will treat it as a fresh start — no renames will be detected until the database is rebuilt.

---

## Logging

All operations are logged to timestamped files:

```text
/path/to/source/logs/
├── s3-sync-moves-2026-01-15_09-12-15.log
├── s3-sync-sync-2026-01-15_09-15-22.log
└── ...
```

---

## Troubleshooting

### "Config file not found"

Run `python3 main.py init` to create the config file, then edit `~/.s3-smart-sync.conf`.

### "Local path does not exist"

Make sure the volume is mounted. External drives must be connected.

### "No renames detected" on first run

This is expected! The first run creates the database. Renames are detected on subsequent runs.

### Missing dependencies

```bash
pip3 install -r requirements.txt
```

---

## File Structure

| File | Purpose |
|------|---------|
| `main.py` | CLI entry point |
| `config.py` | Config file parser for batch mode |
| `scanner.py` | Walks local filesystem, captures inodes |
| `db.py` | SQLite for inode tracking |
| `s3_client.py` | boto3 wrapper with move/upload/delete |
| `diff_engine.py` | Computes sync plan from state differences |
| `archive_handler.py` | Tracks archived objects needing restore |
| `logger.py` | Timestamped logging |
