"""
Sync pipeline orchestrator — ports Java's cdd_sync_main.runSync() and
cdd_sync_crate_fixer helpers to Python.

Entry point: run_sync(config, log_callback=None)
"""

from __future__ import annotations

import logging
import os
from collections import defaultdict
from pathlib import Path
from typing import Callable, Dict, List, Optional

from config import SyncConfig
from core.path_utils import normalize_for_dedup, normalize_path_for_database, resolve_serato_path
from core.serato_parser import Crate, SeratoDatabase, read_crate, write_crate
from sync.backup import create_backup
from sync.database_fixer import update_paths
from sync.dupe_mover import scan_and_move_duplicates
from sync.media_library import MediaLibrary
from sync.pref_sorter import sort_crates

logger = logging.getLogger("cdd_sync")


# ---------------------------------------------------------------------------
# Public entry point
# ---------------------------------------------------------------------------

def run_sync(config: SyncConfig, log_callback: Optional[Callable[[str], None]] = None) -> None:
    """
    Execute the full sync pipeline in the same sequence as Java's runSync().
    All write operations are guarded by config.dry_run.
    """

    def _log(msg: str) -> None:
        logger.info(msg)
        if log_callback:
            log_callback(msg)

    serato_path = config.serato_library_path

    _log("cdd-sync-pro started")

    # ── Backup ───────────────────────────────────────────────────────────────
    if config.backup_enabled:
        if config.dry_run:
            _log(f"[DRY RUN] Would have: created backup of {serato_path}")
        else:
            backup_result = create_backup(serato_path)
            if backup_result is None:
                _log("Backup failed. Aborting sync for safety.")
                return

    # ── Scan media library ───────────────────────────────────────────────────
    _log(f"Scanning media library {config.music_library_path}...")
    fs_library = MediaLibrary.read_from(config.music_library_path)
    if fs_library.total_tracks() <= 0:
        _log("Unable to find any supported files in your media library directory.")
        _log("Are you sure you specified the right path in the config file?")
        return
    _log(f"Found {fs_library.total_tracks()} tracks in {fs_library.total_directories()} directories")

    # ── Step 0 (early): Duplicate move ──────────────────────────────────────
    if config.step0_enabled:
        if config.dupe_move_enabled:
            if config.dry_run:
                _log(f"[DRY RUN] Would have: scanned and moved duplicate files ({config.dupe_move_mode})")
            else:
                moved_to_kept = scan_and_move_duplicates(
                    config.music_library_path, fs_library,
                    config.dupe_detection_mode, config.dupe_move_mode,
                )
                if moved_to_kept:
                    db_path = os.path.join(serato_path, "database V2")
                    db_updated = update_paths(db_path, moved_to_kept)
                    if db_updated > 0:
                        _log(f"Updated {db_updated} paths in database V2 for moved duplicates")
                    _log("Rescanning media library after duplicate removal...")
                    fs_library = MediaLibrary.read_from(config.music_library_path)
                    _log(f"Found {fs_library.total_tracks()} tracks remaining.")
    else:
        _log("Step 0 skipped: duplicate management toggle is off.")

    # ── Validate / create serato_library_path ───────────────────────────────
    _log(f"Writing files into serato library {serato_path}...")
    if not Path(serato_path).is_dir():
        if config.dry_run:
            _log(f"[DRY RUN] Would have: created Serato library folder {serato_path}")
        else:
            try:
                Path(serato_path).mkdir(parents=True, exist_ok=True)
                _log(f"Created Serato library folder: {serato_path}")
            except OSError as exc:
                _log(f"Failed to create Serato library folder: {serato_path} — {exc}")
                return

    # ── Load Serato database ─────────────────────────────────────────────────
    db_file = Path(serato_path) / "database V2"
    database: Optional[SeratoDatabase] = None
    if db_file.exists():
        try:
            database = SeratoDatabase.read_from(db_file)
        except Exception as exc:
            _log(f"Could not parse database V2: {exc}")
    else:
        _log("No existing Serato database found. Skipping path normalization.")

    # ── Validate parent crate ────────────────────────────────────────────────
    parent_crate_path = config.parent_crate_path
    if parent_crate_path:
        _log(f"Using parent crate: {parent_crate_path}")
        subcrates_dir = Path(serato_path) / "Subcrates"
        parent_crate_file = subcrates_dir / f"{parent_crate_path}.crate"
        if not parent_crate_file.exists():
            _log(f"Parent crate '{parent_crate_path}' does not exist. Creating it automatically...")
            if config.dry_run:
                _log(f"[DRY RUN] Would have: created parent crate {parent_crate_path}")
            else:
                try:
                    parent_crate_file.parent.mkdir(parents=True, exist_ok=True)
                    write_crate(Crate(), parent_crate_file)
                except Exception as exc:
                    _log(f"Failed to create parent crate '{parent_crate_path}': {exc}")
                    return

        # Check for duplicate parent crate names
        if subcrates_dir.is_dir():
            count = sum(
                1 for f in subcrates_dir.iterdir()
                if f.name.lower() == f"{parent_crate_path.lower()}.crate"
            )
            if count > 1:
                _log(f"Duplicate parent crate detected: found {count} crates named '{parent_crate_path}'.")
                _log("Please resolve the duplication in Serato before syncing.")
                return

    # ── Load track index (database ref for path encoding) ───────────────────
    # Python: track index == the SeratoDatabase already loaded above.

    # ── Clear library if configured ──────────────────────────────────────────
    if config.clear_library_before_sync:
        if config.dry_run:
            _log("[DRY RUN] Would have: cleared existing Serato library")
        else:
            _delete_dir_contents(Path(serato_path) / "Crates")
            _delete_dir_contents(Path(serato_path) / "Subcrates")
            db_file = Path(serato_path) / "database V2"
            if db_file.exists():
                db_file.unlink()

    # ── Step 1: Fix broken paths in database V2 ──────────────────────────────
    if not config.clear_library_before_sync and config.step1_enabled:
        if config.dry_run:
            _log("[DRY RUN] Would have: updated broken paths in database V2")
        else:
            update_database_paths(serato_path, fs_library)
    elif not config.step1_enabled:
        _log("Step 1 skipped: step1 toggle is off.")
    else:
        _log("Step 1 skipped: Clear Library is on (database was deleted).")

    # ── Step 2: Fix broken paths in existing crates ───────────────────────────
    if not config.clear_library_before_sync and config.step2_enabled:
        if config.dry_run:
            _log("[DRY RUN] Would have: updated crate paths from filesystem")
        else:
            fix_existing_crates(serato_path, fs_library, database)
    elif not config.step2_enabled:
        _log("Step 2 skipped: step2 toggle is off.")
    else:
        _log("Step 2 skipped: Clear Library is on (crates were deleted).")

    # ── Step 3: Append new tracks to existing crates ─────────────────────────
    if config.step3_enabled:
        if config.dry_run:
            _log("[DRY RUN] Would have: appended new tracks to existing crates")
        else:
            append_new_tracks(serato_path, fs_library, parent_crate_path, database)
    else:
        _log("Step 3 skipped: step3 toggle is off.")

    # ── Step 4: Create new crates for new library paths ──────────────────────
    if config.step4_enabled:
        if config.dry_run:
            _log("[DRY RUN] Would have: created new crates for new library paths")
        else:
            create_new_crates(serato_path, fs_library, parent_crate_path, database)
    else:
        _log("Step 4 skipped: step4 toggle is off.")

    # ── Step 0 (late): Log-only duplicate scan ───────────────────────────────
    if config.dupe_scan_enabled and not config.dupe_move_enabled and config.step0_enabled:
        _scan_and_log_duplicates(fs_library)

    _log("Sync Complete")

    # ── Sort crates ───────────────────────────────────────────────────────────
    if config.crate_sorting_enabled:
        if config.dry_run:
            _log("[DRY RUN] Would have: sorted crates alphabetically in neworder.pref")
        else:
            sort_crates(serato_path)

    if config.dry_run:
        _log("[DRY RUN] Sync complete — no files were written.")


# ---------------------------------------------------------------------------
# Step 1 helper — update_database_paths
# ---------------------------------------------------------------------------

def update_database_paths(serato_path: str, library: MediaLibrary) -> None:
    """Fix broken pfil paths in database V2 by filename lookup against the library."""
    logger.info("Step 1: Updating broken paths in database V2...")

    db_file = Path(serato_path) / "database V2"
    if not db_file.exists():
        logger.error("No Serato database V2 found at: %s", serato_path)
        return

    try:
        database = SeratoDatabase.read_from(db_file)
    except Exception as exc:
        logger.error("Failed to read database V2: %s", exc)
        return

    volume_root = get_volume_root(serato_path)
    lib_index = build_library_index(library)

    if not lib_index:
        logger.info("Media library is empty — skipping Step 1.")
        return

    path_fixes: Dict[str, str] = {}
    for db_track_path in database.get_all_track_paths():
        if volume_root:
            abs_path = os.path.join(volume_root, db_track_path)
            if os.path.exists(abs_path):
                continue  # still valid

        filename = os.path.basename(db_track_path).lower()
        candidates = lib_index.get(filename)
        if not candidates:
            continue
        if len(candidates) > 1:
            logger.debug("Ambiguous: %d candidates for %s", len(candidates), filename)
            continue

        fixed = normalize_path_for_database(candidates[0])
        if fixed != db_track_path:
            path_fixes[db_track_path] = fixed

    if not path_fixes:
        logger.info("No broken paths found in database V2.")
        return

    logger.info("Updating database V2 with %d path fixes...", len(path_fixes))
    updated = update_paths(str(db_file), path_fixes)
    logger.info("Updated %d paths in database V2.", updated)


# ---------------------------------------------------------------------------
# Step 2 helper — fix_existing_crates
# ---------------------------------------------------------------------------

def fix_existing_crates(
    serato_path: str,
    library: MediaLibrary,
    database: Optional[SeratoDatabase],
) -> None:
    """Rewrite track paths in existing .crate files using the filesystem as truth."""
    logger.info("Step 2: Rewriting crate paths from filesystem...")

    lib_index = build_library_index(library)
    if not lib_index:
        logger.info("Media library is empty — skipping Step 2.")
        return

    crate_files: List[Path] = []
    collect_crate_files(Path(serato_path) / "Crates", crate_files)
    collect_crate_files(Path(serato_path) / "Subcrates", crate_files)

    if not crate_files:
        logger.info("No crate files found in Crates/ or Subcrates/.")
        return

    logger.info("Step 2: found %d crate files to inspect.", len(crate_files))

    fixed_crates = 0
    fixed_paths = 0

    for crate_file in crate_files:
        try:
            crate = read_crate(crate_file)
        except Exception as exc:
            logger.error("Failed to read crate: %s — %s", crate_file.name, exc)
            continue

        original = list(crate.tracks)
        updated: List[str] = []
        changed = False

        for track_path in original:
            filename = os.path.basename(track_path).lower()
            candidates = lib_index.get(filename)
            if candidates:
                resolved = resolve_serato_path(candidates[0], database)
                new_rel = normalize_path_for_database(resolved)
                if new_rel != track_path:
                    updated.append(new_rel)
                    changed = True
                    fixed_paths += 1
                else:
                    updated.append(track_path)
            else:
                updated.append(track_path)

        if changed:
            crate.set_tracks_raw(updated)
            try:
                write_crate(crate, crate_file)
                fixed_crates += 1
            except Exception as exc:
                logger.error("Failed to write crate: %s — %s", crate_file.name, exc)

    logger.info("Step 2 complete: %d paths fixed across %d crates.", fixed_paths, fixed_crates)


# ---------------------------------------------------------------------------
# Step 3 helper — append_new_tracks
# ---------------------------------------------------------------------------

def append_new_tracks(
    serato_path: str,
    library: MediaLibrary,
    parent_crate_path: Optional[str],
    database: Optional[SeratoDatabase],
) -> None:
    """Append new tracks to existing crate files (never create new crates)."""
    logger.info("Step 3: Appending new tracks to existing crates...")

    subcrates_dir = Path(serato_path) / "Subcrates"
    if not subcrates_dir.exists():
        logger.info("No Subcrates directory found, skipping append step.")
        return

    crate_map = build_crate_file_map(library, parent_crate_path)
    appended = 0
    skipped = 0

    for crate_filename, new_tracks in crate_map.items():
        crate_file = subcrates_dir / crate_filename
        if not crate_file.exists():
            continue  # Step 4 will create it

        try:
            crate = read_crate(crate_file)
        except Exception as exc:
            logger.error("Failed to read crate for append: %s — %s", crate_filename, exc)
            continue

        before = len(crate.tracks)
        if database:
            crate.set_database(database)
        for track in new_tracks:
            crate.add_track(track)
        after = len(crate.tracks)

        if after > before:
            try:
                write_crate(crate, crate_file)
                appended += 1
            except Exception as exc:
                logger.error("Failed to write appended crate: %s — %s", crate_filename, exc)
        else:
            skipped += 1

    if appended > 0:
        logger.info("Step 3 complete: %d crates updated.", appended)
    else:
        logger.info("Step 3 complete: no new tracks to append (%d unchanged).", skipped)


# ---------------------------------------------------------------------------
# Step 4 helper — create_new_crates
# ---------------------------------------------------------------------------

def create_new_crates(
    serato_path: str,
    library: MediaLibrary,
    parent_crate_path: Optional[str],
    database: Optional[SeratoDatabase],
) -> None:
    """Create new .crate files for library folders that have no matching crate yet."""
    logger.info("Step 4: Creating new crates for new library paths...")

    subcrates_dir = Path(serato_path) / "Subcrates"
    crate_map = build_crate_file_map(library, parent_crate_path)
    created = 0
    skipped = 0

    for crate_filename, tracks in crate_map.items():
        crate_file = subcrates_dir / crate_filename
        if crate_file.exists():
            skipped += 1
            continue

        crate = Crate()
        if database:
            crate.set_database(database)
        crate.add_tracks(tracks)

        try:
            crate_file.parent.mkdir(parents=True, exist_ok=True)
            write_crate(crate, crate_file)
            created += 1
        except Exception as exc:
            logger.error("Failed to write new crate: %s — %s", crate_filename, exc)

    if created > 0:
        logger.info("Step 4 complete: %d new crates created (%d existing skipped).", created, skipped)
    else:
        logger.info("Step 4 complete: no new crates needed (%d existing skipped).", skipped)


# ---------------------------------------------------------------------------
# Shared helpers
# ---------------------------------------------------------------------------

def build_library_index(library: MediaLibrary) -> Dict[str, List[str]]:
    """Map lowercase filename → list of absolute paths from the scanned library."""
    index: Dict[str, List[str]] = defaultdict(list)
    for path in library.flatten_tracks():
        filename = os.path.basename(path).lower()
        index[filename].append(path)
    return dict(index)


def build_crate_file_map(
    library: MediaLibrary,
    parent_crate_path: Optional[str],
) -> Dict[str, List[str]]:
    """Map crate filename (e.g. 'Parent%%Child.crate') → direct track list for that folder."""
    result: Dict[str, List[str]] = {}
    root_name = parent_crate_path if parent_crate_path else ""
    _build_crate_file_map_recursive(library, root_name, 0, result)
    return result


def _build_crate_file_map_recursive(
    node: MediaLibrary,
    crate_name: str,
    level: int,
    result: Dict[str, List[str]],
) -> None:
    if level > 0:
        result[f"{crate_name}.crate"] = list(node.tracks)
    for child in node.children:
        child_name = (
            child.directory if not crate_name
            else f"{crate_name}%%{child.directory}"
        )
        _build_crate_file_map_recursive(child, child_name, level + 1, result)


def collect_crate_files(directory: Path, result: List[Path]) -> None:
    """Recursively collect all .crate files under *directory* into *result*."""
    if not directory.is_dir():
        return
    for entry in directory.iterdir():
        if entry.is_dir():
            collect_crate_files(entry, result)
        elif entry.suffix == ".crate":
            result.append(entry)


def get_volume_root(serato_path: str) -> Optional[str]:
    """
    Return the parent directory of _Serato_, or None if path doesn't end in _Serato_.
    e.g. /Volumes/Current/_Serato_ → /Volumes/Current
    """
    p = Path(serato_path)
    if p.name.lower() == "_serato_":
        return str(p.parent)
    return None


# ---------------------------------------------------------------------------
# Late Step 0 — log-only dupe scan
# ---------------------------------------------------------------------------

def _scan_and_log_duplicates(library: MediaLibrary) -> None:
    logger.info("Scanning for hard drive duplicates...")
    all_tracks = library.flatten_tracks()
    groups: Dict[str, List[str]] = defaultdict(list)
    for path in all_tracks:
        try:
            size = os.path.getsize(path)
        except OSError:
            size = 0
        key = os.path.basename(path).lower() + "|" + str(size)
        groups[key].append(path)

    dupe_count = sum(1 for paths in groups.values() if len(paths) > 1)
    if dupe_count > 0:
        logger.info("Found %d duplicate file groups on hard drive.", dupe_count)
    else:
        logger.info("No hard drive duplicates found.")


# ---------------------------------------------------------------------------
# Utility
# ---------------------------------------------------------------------------

def _delete_dir_contents(directory: Path) -> None:
    """Delete all files (non-recursively) inside *directory*, if it exists."""
    if not directory.is_dir():
        return
    for entry in directory.iterdir():
        if entry.is_file():
            try:
                entry.unlink()
            except OSError as exc:
                logger.error("Failed to delete %s: %s", entry, exc)
