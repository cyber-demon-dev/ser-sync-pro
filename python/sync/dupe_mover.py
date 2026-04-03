"""
DupeMover — scans for duplicate tracks and moves copies to a timestamped folder.

Mirrors Java's cdd_sync_dupe_mover.scanAndMoveDuplicates():
  - Detection keys: "name-and-size", "name-only", "off"
  - Move modes: "keep-newest" (move older), "keep-oldest" (move newer)
  - Destination: <library_parent>/cdd-sync-pro/dupes/<timestamp>/
  - Writes dupes.log matching Java's writeLogFile() format
"""

from __future__ import annotations

import logging
import os
import shutil
from datetime import datetime
from pathlib import Path
from typing import Dict, List

from sync.media_library import MediaLibrary

logger = logging.getLogger("cdd_sync")

_DUPES_FOLDER = "cdd-sync-pro/dupes"
_KEEP_NEWEST = "keep-newest"
_KEEP_OLDEST = "keep-oldest"


def scan_and_move_duplicates(
    music_library_root: str,
    library: MediaLibrary,
    detection_mode: str,
    move_mode: str,
) -> Dict[str, str]:
    """
    Scan *library* for duplicates and move copies to a timestamped dupes folder.

    Returns a dict of {moved_path: kept_path} for database path-update use.
    """
    logger.info("Duplicate detection mode: %s", detection_mode)

    if detection_mode == "off":
        logger.info("Duplicate detection is disabled.")
        return {}

    logger.info("Scanning for duplicates to move...")

    if move_mode == _KEEP_NEWEST:
        logger.info("Move strategy: Keep newest, move older files")
    else:
        logger.info("Move strategy: Keep oldest, move newer files")

    # Flatten all tracks
    all_tracks: List[str] = library.flatten_tracks()
    logger.info("Total tracks scanned: %d", len(all_tracks))

    # Group by detection key
    groups: Dict[str, List[str]] = {}
    for path in all_tracks:
        filename = os.path.basename(path).lower()
        if detection_mode == "name-only":
            key = filename
        elif detection_mode == "name-and-size":
            try:
                size = os.path.getsize(path)
            except OSError:
                size = 0
            key = f"{filename}|{size}"
        else:
            logger.error("Invalid detection mode '%s', defaulting to name-and-size", detection_mode)
            try:
                size = os.path.getsize(path)
            except OSError:
                size = 0
            key = f"{filename}|{size}"
        groups.setdefault(key, []).append(path)

    if detection_mode == "name-only":
        logger.info("Total unique filenames: %d", len(groups))
    else:
        logger.info("Total unique filename+size combinations: %d", len(groups))

    # Find duplicate groups (>1 member)
    dupe_groups = {k: v for k, v in groups.items() if len(v) > 1}

    if not dupe_groups:
        logger.info("No duplicates found.")
        return {}

    total_groups = len(dupe_groups)
    logger.info("Found %d duplicate groups.", total_groups)

    # Create timestamped dupes folder
    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    library_parent = Path(music_library_root).parent
    dupes_root = library_parent / _DUPES_FOLDER / timestamp

    if dupes_root.exists():
        logger.error("Dupes folder already exists: %s", dupes_root)
        logger.error("This should not happen with timestamped folders. Aborting.")
        return {}

    try:
        dupes_root.mkdir(parents=True, exist_ok=False)
    except OSError as exc:
        logger.error("Failed to create dupes folder: %s — %s", dupes_root, exc)
        return {}

    moved_to_kept: Dict[str, str] = {}
    log_entries: List[str] = []
    total_moved = 0

    for group_key, paths in dupe_groups.items():
        keep_newest = move_mode == _KEEP_NEWEST
        paths_sorted = sorted(
            paths,
            key=lambda p: _mtime(p),
            reverse=keep_newest,  # descending → newest first when keep_newest
        )

        kept_path = paths_sorted[0]
        kept_date = datetime.fromtimestamp(_mtime(kept_path)).strftime("%Y-%m-%d")

        log_entries.append(f"Duplicate group: {group_key}")
        log_entries.append(f"  KEPT:  {kept_path} ({kept_date})")

        for move_path in paths_sorted[1:]:
            move_date = datetime.fromtimestamp(_mtime(move_path)).strftime("%Y-%m-%d")
            rel = _relative_path(move_path, music_library_root)
            dest = dupes_root / rel
            dest.parent.mkdir(parents=True, exist_ok=True)

            try:
                shutil.move(move_path, str(dest))
                log_entries.append(f"  MOVED: {move_path} ({move_date})")
                log_entries.append(f"      -> {dest}")
                moved_to_kept[move_path] = kept_path
                total_moved += 1
            except OSError as exc:
                log_entries.append(f"  ERROR: Failed to move {move_path}: {exc}")
                logger.error("Failed to move file: %s — %s", move_path, exc)

        log_entries.append("")

    # Write dupes.log
    log_file = dupes_root / "dupes.log"
    _write_log(log_file, timestamp, total_groups, total_moved, log_entries)

    logger.info("Moved %d duplicate files to: %s", total_moved, dupes_root)
    logger.info("See %s for details.", log_file)

    return moved_to_kept


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _mtime(path: str) -> float:
    try:
        return os.path.getmtime(path)
    except OSError:
        return 0.0


def _relative_path(file_path: str, library_root: str) -> str:
    norm_file = file_path.replace("\\", "/")
    norm_root = library_root.replace("\\", "/")
    if not norm_root.endswith("/"):
        norm_root += "/"
    if norm_file.startswith(norm_root):
        return norm_file[len(norm_root):]
    return os.path.basename(file_path)


def _write_log(
    log_file: Path,
    timestamp: str,
    total_groups: int,
    total_moved: int,
    entries: List[str],
) -> None:
    try:
        with log_file.open("w", encoding="utf-8") as fh:
            fh.write("=== Duplicate File Scan Report ===\n")
            fh.write(f"Date: {timestamp.replace('_', ' ')}\n")
            fh.write(f"Total duplicate groups found: {total_groups}\n")
            fh.write(f"Total files moved: {total_moved}\n")
            fh.write("=====================================\n\n")
            for entry in entries:
                fh.write(entry + "\n")
    except OSError as exc:
        logger.error("Failed to write dupes log: %s", exc)
