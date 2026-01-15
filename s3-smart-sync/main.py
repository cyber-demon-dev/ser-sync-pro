"""
S3 Smart Sync - Main CLI Entry Point

A bandwidth-efficient S3 sync tool that detects local file renames
and performs server-side moves instead of re-uploading.

Usage:
    python main.py sync <source_path> <bucket_name> [--prefix PREFIX] [--delete]
    python main.py status <source_path>
    python main.py restore <bucket_name> [--prefix PREFIX] [--tier bulk|standard|expedited]
    python main.py restore-status <bucket_name> [--prefix PREFIX]
"""

import argparse
import os
import sys
from pathlib import Path


def check_dependencies():
    """Check that all required dependencies are installed."""
    missing = []
    
    # Check boto3
    try:
        import boto3
    except ImportError:
        missing.append("boto3")
    
    if missing:
        print("=" * 60)
        print("ERROR: Missing required dependencies!")
        print("=" * 60)
        print()
        print("The following packages are not installed:")
        for pkg in missing:
            print(f"  - {pkg}")
        print()
        print("To install, run:")
        print("  pip3 install -r requirements.txt")
        print()
        print("Or install manually:")
        print(f"  pip3 install {' '.join(missing)}")
        print()
        sys.exit(1)


# Run dependency check immediately
check_dependencies()

# Now import the rest (these depend on boto3)
from db import Database, FileRecord
from scanner import Scanner
from s3_client import S3Client
from diff_engine import DiffEngine
from archive_handler import ArchiveHandler
from logger import Logger


# Default database location
DEFAULT_DB_NAME = ".s3-smart-sync.db"


def get_db_path(source_path: str) -> str:
    """Get the database path for a given source directory."""
    return os.path.join(source_path, DEFAULT_DB_NAME)


def cmd_sync(args):
    """Execute the sync command."""
    source_path = os.path.abspath(args.source)
    bucket_name = args.bucket
    prefix = args.prefix or ""
    delete_orphans = args.delete
    dry_run = args.dry_run
    
    # Initialize logger
    logger = Logger(source_path, "sync")
    logger.start()
    
    def log(msg):
        logger.log(msg)
    
    log(f"S3 Smart Sync")
    log(f"{'=' * 50}")
    log(f"Source: {source_path}")
    log(f"Bucket: s3://{bucket_name}/{prefix}")
    log(f"Delete orphans: {delete_orphans}")
    log(f"Dry run: {dry_run}")
    log("")
    
    # Validate source
    if not os.path.isdir(source_path):
        log(f"Error: Source path does not exist or is not a directory: {source_path}")
        logger.close()
        sys.exit(1)
    
    # Initialize components
    db_path = get_db_path(source_path)
    db = Database(db_path)
    db.connect()
    
    scanner = Scanner(source_path)
    s3 = S3Client(bucket_name, prefix)
    
    try:
        # Step 1: Load previous state
        log("[1/5] Loading previous state...")
        previous_records = db.get_all_records()
        log(f"      Found {len(previous_records)} previously tracked files")
        
        # Step 2: Scan current local files
        log("[2/5] Scanning local files...")
        current_files = scanner.scan_to_dict()
        log(f"      Found {len(current_files)} local files")
        
        # Step 3: List S3 objects
        log("[3/5] Listing S3 objects...")
        s3_objects = s3.list_objects()
        log(f"      Found {len(s3_objects)} objects in S3")
        
        # Step 4: Compute diff
        log("[4/5] Computing sync plan...")
        engine = DiffEngine(current_files, previous_records, s3_objects, delete_orphans)
        plan = engine.compute_plan()
        log(f"      {plan.summary()}")
        
        if not plan.moves and not plan.uploads and not plan.deletes:
            log("\n✓ Everything is in sync!")
            return
        
        # Step 5: Execute plan
        log("[5/5] Executing sync plan...")
        log("")
        
        if dry_run:
            log("=== DRY RUN MODE - No changes will be made ===")
            log("")
        
        # Initialize archive handler for tracking Glacier/Deep Archive objects
        archive_handler = ArchiveHandler(source_path)
        
        # Execute moves first (bandwidth-efficient renames)
        if plan.moves:
            log(f"--- Server-Side Moves ({len(plan.moves)}) ---")
            for old_key, new_key in plan.moves:
                if dry_run:
                    log(f"  [MOVE] {old_key} -> {new_key}")
                else:
                    result = s3.move_object(old_key, new_key, archive_handler)
                    log(f"  [MOVE] {old_key} -> {new_key} ({result})", also_print=False)
            log("")
        
        # Execute deletes (if enabled)
        if plan.deletes:
            log(f"--- Deletes ({len(plan.deletes)}) ---")
            if dry_run:
                for key in plan.deletes:
                    log(f"  [DELETE] {key}")
            else:
                s3.delete_objects(plan.deletes)
            log("")
        
        # Execute uploads last
        if plan.uploads:
            log(f"--- Uploads ({len(plan.uploads)}) ---")
            for local_path, key in plan.uploads:
                if dry_run:
                    log(f"  [UPLOAD] {key}")
                else:
                    s3.upload_file(local_path, key)
            log("")
        
        # Update database with current state
        if not dry_run:
            log("Updating local database...")
            records = [
                FileRecord(
                    path=f.path,
                    inode=f.inode,
                    size=f.size,
                    mtime=f.mtime,
                    synced=True
                )
                for f in current_files.values()
            ]
            db.bulk_upsert(records)
            
            # Clean up deleted paths from DB
            current_paths = {f.path for f in current_files.values()}
            db.delete_missing_paths(current_paths)
        
        # Save archive log if any objects need restoration
        if not dry_run and archive_handler.has_pending():
            archive_handler.save_log()
            log(f"\n⚠ Some objects are archived and need restoration.")
            log(f"  Run: python main.py restore {bucket_name} --prefix '{prefix}'")
        
        log("")
        log("✓ Sync complete!")
        
    finally:
        db.close()
        logger.close()


def cmd_status(args):
    """Show sync status for a source path."""
    source_path = os.path.abspath(args.source)
    
    print(f"S3 Smart Sync - Status")
    print(f"{'=' * 50}")
    print(f"Source: {source_path}")
    print()
    
    db_path = get_db_path(source_path)
    
    if not os.path.exists(db_path):
        print("Status: Never synced (no database found)")
        return
    
    db = Database(db_path)
    db.connect()
    
    try:
        records = db.get_all_records()
        print(f"Tracked files: {len(records)}")
        
        # Quick scan to check for changes
        scanner = Scanner(source_path)
        current_files = scanner.scan_to_dict()
        
        # Simple diff stats
        new_inodes = set(current_files.keys()) - set(records.keys())
        missing_inodes = set(records.keys()) - set(current_files.keys())
        
        renames = 0
        modified = 0
        for inode in set(current_files.keys()) & set(records.keys()):
            curr = current_files[inode]
            prev = records[inode]
            if curr.path != prev.path:
                renames += 1
            elif curr.size != prev.size or curr.mtime > prev.mtime:
                modified += 1
        
        print()
        print("Changes since last sync:")
        print(f"  New files:      {len(new_inodes)}")
        print(f"  Renamed/moved:  {renames}")
        print(f"  Modified:       {modified}")
        print(f"  Deleted:        {len(missing_inodes)}")
        
    finally:
        db.close()


def cmd_restore(args):
    """Initiate restore for archived S3 objects."""
    source_path = os.path.abspath(args.source)
    bucket_name = args.bucket
    prefix = args.prefix or ""
    tier = args.tier.capitalize()  # Bulk, Standard, Expedited
    days = args.days
    
    print(f"S3 Smart Sync - Restore Archived Objects")
    print(f"{'=' * 50}")
    print(f"Bucket: s3://{bucket_name}/{prefix}")
    print(f"Tier: {tier}")
    print(f"Days: {days}")
    print()
    
    # Load pending keys from archive log
    archive_handler = ArchiveHandler(source_path)
    pending_keys = archive_handler.load_pending_keys()
    
    if not pending_keys:
        print("No archived objects found in restore log.")
        print(f"(Looking for: {archive_handler.log_path})")
        return
    
    print(f"Found {len(pending_keys)} object(s) to restore")
    print()
    
    # Initialize S3 client
    s3 = S3Client(bucket_name, prefix)
    
    # Initiate restore for each key
    success_count = 0
    for key in pending_keys:
        if s3.restore_object(key, days=days, tier=tier):
            success_count += 1
    
    print()
    print(f"Initiated restore for {success_count}/{len(pending_keys)} objects")
    
    if tier == 'Bulk':
        print("Expected restore time: 12-48 hours")
    elif tier == 'Standard':
        print("Expected restore time: 3-5 hours")
    else:
        print("Expected restore time: 1-5 minutes")
    
    print()
    print("Run 'restore-status' to check progress, then run 'sync' again after restore completes.")


def cmd_restore_status(args):
    """Check restore status for archived objects."""
    source_path = os.path.abspath(args.source)
    bucket_name = args.bucket
    prefix = args.prefix or ""
    
    print(f"S3 Smart Sync - Restore Status")
    print(f"{'=' * 50}")
    print(f"Bucket: s3://{bucket_name}/{prefix}")
    print()
    
    # Load pending keys from archive log
    archive_handler = ArchiveHandler(source_path)
    pending_keys = archive_handler.load_pending_keys()
    
    if not pending_keys:
        print("No archived objects found in restore log.")
        return
    
    # Initialize S3 client
    s3 = S3Client(bucket_name, prefix)
    
    # Check status for each key
    status_counts = {'not_started': 0, 'in_progress': 0, 'completed': 0, 'error': 0}
    
    print(f"Checking {len(pending_keys)} object(s)...")
    print()
    
    for key in pending_keys:
        status = s3.get_restore_status(key)
        restore_status = status.get('restore_status', 'unknown')
        storage_class = status.get('storage_class', 'UNKNOWN')
        
        status_counts[restore_status] = status_counts.get(restore_status, 0) + 1
        
        icon = {
            'not_started': '⏳',
            'in_progress': '🔄',
            'completed': '✅',
            'error': '❌'
        }.get(restore_status, '❓')
        
        print(f"  {icon} [{restore_status.upper()}] {key} ({storage_class})")
    
    print()
    print("Summary:")
    print(f"  Completed:    {status_counts.get('completed', 0)}")
    print(f"  In Progress:  {status_counts.get('in_progress', 0)}")
    print(f"  Not Started:  {status_counts.get('not_started', 0)}")
    print(f"  Errors:       {status_counts.get('error', 0)}")
    
    if status_counts.get('completed', 0) == len(pending_keys):
        print()
        print("✓ All objects restored! You can now run 'sync' again.")
        # Optionally clear the log
        archive_handler.clear_log()


def main():
    parser = argparse.ArgumentParser(
        description="S3 Smart Sync - Bandwidth-efficient S3 sync with rename detection"
    )
    subparsers = parser.add_subparsers(dest='command', help='Commands')
    
    # Sync command
    sync_parser = subparsers.add_parser('sync', help='Sync local directory to S3')
    sync_parser.add_argument('source', help='Local source directory')
    sync_parser.add_argument('bucket', help='S3 bucket name')
    sync_parser.add_argument('--prefix', '-p', help='S3 prefix (folder)', default='')
    sync_parser.add_argument('--delete', '-d', action='store_true',
                             help='Delete S3 files not found locally (mirror mode)')
    sync_parser.add_argument('--dry-run', '-n', action='store_true',
                             help='Show what would be done without making changes')
    sync_parser.set_defaults(func=cmd_sync)
    
    # Status command
    status_parser = subparsers.add_parser('status', help='Show sync status')
    status_parser.add_argument('source', help='Local source directory')
    status_parser.set_defaults(func=cmd_status)
    
    # Restore command
    restore_parser = subparsers.add_parser('restore', help='Initiate restore for archived objects')
    restore_parser.add_argument('source', help='Local source directory (contains restore log)')
    restore_parser.add_argument('bucket', help='S3 bucket name')
    restore_parser.add_argument('--prefix', '-p', help='S3 prefix (folder)', default='')
    restore_parser.add_argument('--tier', '-t', choices=['bulk', 'standard', 'expedited'],
                                default='bulk', help='Restore tier (default: bulk)')
    restore_parser.add_argument('--days', '-d', type=int, default=7,
                                help='Days to keep restored copy available (default: 7)')
    restore_parser.set_defaults(func=cmd_restore)
    
    # Restore-status command
    restore_status_parser = subparsers.add_parser('restore-status', help='Check restore status')
    restore_status_parser.add_argument('source', help='Local source directory (contains restore log)')
    restore_status_parser.add_argument('bucket', help='S3 bucket name')
    restore_status_parser.add_argument('--prefix', '-p', help='S3 prefix (folder)', default='')
    restore_status_parser.set_defaults(func=cmd_restore_status)
    
    args = parser.parse_args()
    
    if not args.command:
        parser.print_help()
        sys.exit(1)
    
    args.func(args)


if __name__ == '__main__':
    main()
