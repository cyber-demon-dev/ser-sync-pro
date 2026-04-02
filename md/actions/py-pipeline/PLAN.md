# Python Pipeline (py-pipeline) — Implementation Plan

> For: Another agent to execute.
> Scope: Port the sync pipeline from Java to Python. Covers config, media library scanner, backup, dupe mover, pref sorter, 4-step pipeline orchestrator, and CLI entrypoint. GUI deferred.
> Feature dir: `md/actions/py-pipeline/`
> Feature abbr: `py-pipeline`
> Constraint: One concern per phase. No deviations. No fallback paths.
> Executor: On failure — stop immediately, report exact output, do not retry.
> Java reference: `java/` is READ-ONLY. Read Java files as specification only. Never modify them.

---

## Context

| Item | Value |
|------|-------|
| Project root | `/Users/culprit/Git/cdd-sync-pro` |
| Working branch | `python` |
| Runtime | Python 3.12+ |
| Test command | `cd python && python3 -m pytest tests/ -v` |
| Env vars required | NONE |
| Key dependencies | `pyyaml>=6.0` (already in requirements.txt), `flet>=0.21` (deferred) |
| Foundation | `python/core/` already exists — do NOT modify those files |
| Java reference files | See each phase — every phase names its Java source(s) |
| Current state | `python/` has `core/` (path_utils, binary_utils, serato_parser) and `tests/` (17 passing). All other modules are missing. |

---

## Phase 1 — Config (`python/config.py`)

**File(s):** `python/config.py`

**Java reference:** `java/cdd-sync-pro/src/cdd_sync_config.java` — read in full before writing.

**Action:** Create a `SyncConfig` dataclass that loads from and saves to a YAML file (`config.yaml`). Replace Java's `.properties` format with YAML. Map every Java getter to a Python attribute with the same default value.

**Contract:**
- Class: `SyncConfig`
- Constructor: `SyncConfig.load(path: Path = Path("config.yaml")) -> SyncConfig` — reads YAML, applies defaults if key missing
- Method: `save(path: Path = Path("config.yaml")) -> None` — writes current values to YAML (pyyaml dump)
- Attributes (name → Java getter → default):
  - `music_library_path: str` → `getMusicLibraryPath()` → required (raise `ValueError` if missing)
  - `serato_library_path: str` → `getSeratoLibraryPath()` → required (raise `ValueError` if missing)
  - `parent_crate_path: Optional[str]` → `getParentCratePath()` → `None`
  - `clear_library_before_sync: bool` → `isClearLibraryBeforeSync()` → `False`
  - `backup_enabled: bool` → `isBackupEnabled()` → `True`
  - `dupe_scan_enabled: bool` → `isHardDriveDupeScanEnabled()` → `False`
  - `dupe_move_mode: str` → `getDupeMoveMode()` → `"off"` (values: `"keep-newest"`, `"keep-oldest"`, `"off"`)
  - `dupe_detection_mode: str` → `getDupeDetectionMode()` → `"off"` (values: `"name-and-size"`, `"name-only"`, `"off"`)
  - `crate_sorting_enabled: bool` → `isCrateSortingEnabled()` → `False`
  - `step0_enabled: bool` → `isStep0Enabled()` → `True`
  - `step1_enabled: bool` → `isStep1Enabled()` → `True`
  - `step2_enabled: bool` → `isStep2Enabled()` → `True`
  - `step3_enabled: bool` → `isStep3Enabled()` → `True`
  - `step4_enabled: bool` → `isStep4Enabled()` → `True`
  - `dry_run: bool` → `isDryRun()` → `False`
- `parent_crate_path` must validate: reject values containing `%%` (raise `ValueError` with message: `"Invalid parent_crate_path: nested paths not supported"`)
- `dupe_move_enabled` property: returns `True` if `dupe_move_mode` in `("keep-newest", "keep-oldest")`
- YAML key mapping — use these exact keys in the YAML file:
  - `music_library_path`, `serato_library_path`, `parent_crate_path`
  - `clear_library_before_sync`, `backup_enabled`
  - `dupe_scan_enabled`, `dupe_move_mode`, `dupe_detection_mode`
  - `crate_sorting_enabled`
  - `step0_enabled` through `step4_enabled`
  - `dry_run`

**Config template file:** Also create `python/config.template.yaml` — a commented-out template showing all keys with their defaults. Use `#` comments to explain each group (Paths, Options, Deduplication, Pipeline Steps).

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro && python3 -c "
import sys; sys.path.insert(0, 'python')
from config import SyncConfig
from pathlib import Path
import tempfile, yaml, os

# Test defaults
cfg = SyncConfig(music_library_path='/tmp/music', serato_library_path='/tmp/serato')
assert cfg.backup_enabled == True
assert cfg.dry_run == False
assert cfg.dupe_move_enabled == False

# Test save/load round-trip
with tempfile.NamedTemporaryFile(suffix='.yaml', delete=False, mode='w') as f:
    tmp = Path(f.name)
cfg.save(tmp)
cfg2 = SyncConfig.load(tmp)
assert cfg2.music_library_path == '/tmp/music'
assert cfg2.backup_enabled == True
tmp.unlink()

# Test nested path rejection
try:
    SyncConfig(music_library_path='/x', serato_library_path='/y', parent_crate_path='A%%B')
    assert False, 'Should have raised'
except ValueError:
    pass

print('Phase 1 config: ALL PASS')
"
```
Expected: `Phase 1 config: ALL PASS`

---

## Phase 2 — Media Library Scanner (`python/sync/media_library.py`)

**File(s):** `python/sync/__init__.py`, `python/sync/media_library.py`

**Java reference:** `java/shared/src/cdd_sync_media_library.java` — read in full before writing.

**Action:** Port the recursive media library scanner. Python uses `os.scandir` + `concurrent.futures.ThreadPoolExecutor` for parallel subdirectory scanning (mirrors Java's ForkJoinPool pattern).

**Contract:**
- Class: `MediaLibrary`
- Constructor: `MediaLibrary(directory: str)`
- Attributes: `directory: str`, `tracks: list[str]` (sorted), `children: list[MediaLibrary]` (sorted by directory name)
- `MEDIA_EXTENSIONS: frozenset` — same set as Java (`.mp3 .flac .wav .ogg .aif .aiff .aac .alac .m4a .mov .mp4 .avi .flv .mpg .mpeg .dv .qtz`)
- Classmethod: `MediaLibrary.read_from(path: str) -> MediaLibrary`
- `total_tracks() -> int` — recursive count
- `total_directories() -> int` — recursive count
- `flatten_tracks() -> list[str]` — flat list, depth-first
- `remove_tracks(paths: list[str]) -> int` — remove from self and all children; return count removed
- Parallel scanning: use `ThreadPoolExecutor(max_workers=min(4, os.cpu_count()))` for subdirs when `len(subdirs) > 1`
- Track paths must be resolved to real paths (`Path(f).resolve()` — mirrors Java's `toRealPath()`)
- `children` list must be sorted alphabetically by `directory` name

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro && python3 -c "
import sys; sys.path.insert(0, 'python')
from sync.media_library import MediaLibrary
import tempfile, os
from pathlib import Path

# Build a temp tree with mp3 files
with tempfile.TemporaryDirectory() as root:
    os.makedirs(f'{root}/A')
    os.makedirs(f'{root}/B')
    Path(f'{root}/A/track1.mp3').write_bytes(b'x')
    Path(f'{root}/A/track2.flac').write_bytes(b'x')
    Path(f'{root}/B/track3.mp3').write_bytes(b'x')
    Path(f'{root}/ignore.txt').write_bytes(b'x')

    lib = MediaLibrary.read_from(root)
    assert lib.total_tracks() == 3, f'Expected 3 tracks, got {lib.total_tracks()}'
    assert lib.total_directories() == 2, f'Expected 2 dirs, got {lib.total_directories()}'
    flat = lib.flatten_tracks()
    assert len(flat) == 3
    assert all(f.endswith(('.mp3', '.flac')) for f in flat)

print('Phase 2 media_library: ALL PASS')
"
```
Expected: `Phase 2 media_library: ALL PASS`

---

## Phase 3 — Backup (`python/sync/backup.py`)

**File(s):** `python/sync/backup.py`

**Java reference:** `java/shared/src/cdd_sync_backup.java` — read in full before writing.

**Action:** Port the Serato folder backup utility. Python uses `shutil.copytree` with `copy_function=shutil.copy2` (preserves timestamps — mirrors Java's `COPY_ATTRIBUTES`).

**Contract:**
- Function: `create_backup(serato_path: str) -> Optional[str]`
  - Returns: absolute path of backup directory, or `None` on failure
  - Backup root: `<volume_root>/cdd-sync-pro/backup/` (sibling to `_Serato_`)
  - Backup name: `<epoch_ms>_Serato_` (e.g. `1711234567890_Serato_`)
  - Uses `shutil.copytree(serato_path, backup_dir, copy_function=shutil.copy2)`
  - Logs: `"Creating backup: <path>"` on start, `"Backup complete (<size>)"` on success
  - On error: log the exception message, return `None`
- Function: `format_size(size_bytes: int) -> str` — use the existing one in `core/binary_utils.py` (import it, don't duplicate)
- Logging: use Python's built-in `logging` module, logger name `"cdd_sync"`. All log calls in all sync modules must use this same logger name.

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro && python3 -c "
import sys; sys.path.insert(0, 'python')
from sync.backup import create_backup
import tempfile, os
from pathlib import Path

# Build fake _Serato_ dir
with tempfile.TemporaryDirectory() as vol:
    serato = Path(vol) / '_Serato_'
    serato.mkdir()
    (serato / 'database V2').write_bytes(b'vrsn\x00\x00')
    (serato / 'Subcrates').mkdir()
    (serato / 'Subcrates' / 'test.crate').write_bytes(b'vrsn\x00\x00')

    result = create_backup(str(serato))
    assert result is not None, 'Backup returned None'
    backup_path = Path(result)
    assert backup_path.exists(), f'Backup dir does not exist: {result}'
    assert (backup_path / 'database V2').exists(), 'database V2 not backed up'
    assert (backup_path / 'Subcrates' / 'test.crate').exists(), 'crate not backed up'

print('Phase 3 backup: ALL PASS')
"
```
Expected: `Phase 3 backup: ALL PASS`

---

## Phase 4 — Dupe Mover (`python/sync/dupe_mover.py`)

**File(s):** `python/sync/dupe_mover.py`

**Java reference:** `java/cdd-sync-pro/src/cdd_sync_dupe_mover.java` — read in full before writing.

**Action:** Port the duplicate file scanner and mover.

**Contract:**
- Function: `scan_and_move_duplicates(music_library_root: str, library, detection_mode: str, move_mode: str) -> dict[str, str]`
  - Returns: `{moved_path: kept_path}` map (empty dict if detection is `"off"`)
  - `detection_mode`: `"name-and-size"` (key = `filename.lower() + "|" + str(size)`), `"name-only"` (key = `filename.lower()`), `"off"` (return immediately)
  - `move_mode`: `"keep-newest"` (sort by mtime descending → keep index 0), `"keep-oldest"` (sort by mtime ascending → keep index 0)
  - Dupes folder: `<library_parent>/cdd-sync-pro/dupes/<timestamp>/` where `timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")`
  - After moving: write `dupes.log` inside the timestamp folder. Log format: identical to Java (see `writeLogFile()` in Java source)
  - All file moves use `shutil.move(src, dst)` — create parent dirs with `os.makedirs(exist_ok=True)` first
  - On `OSError` during move: log error, skip that file, continue
- Logging: use logger `"cdd_sync"` (same as backup.py)

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro && python3 -c "
import sys; sys.path.insert(0, 'python')
from sync.dupe_mover import scan_and_move_duplicates
from sync.media_library import MediaLibrary
import tempfile, os
from pathlib import Path

with tempfile.TemporaryDirectory() as vol:
    root = Path(vol) / 'music'
    a = root / 'A'; a.mkdir(parents=True)
    b = root / 'B'; b.mkdir(parents=True)
    (a / 'track.mp3').write_bytes(b'x' * 100)
    import time; time.sleep(0.05)
    (b / 'track.mp3').write_bytes(b'x' * 100)  # newer

    lib = MediaLibrary.read_from(str(root))
    moved = scan_and_move_duplicates(str(root), lib, 'name-and-size', 'keep-newest')
    assert len(moved) == 1, f'Expected 1 moved, got {len(moved)}'
    kept = list(moved.values())[0]
    assert 'track.mp3' in kept

print('Phase 4 dupe_mover: ALL PASS')
"
```
Expected: `Phase 4 dupe_mover: ALL PASS`

---

## Phase 5 — Pref Sorter (`python/sync/pref_sorter.py`)

**File(s):** `python/sync/pref_sorter.py`

**Java reference:** `java/cdd-sync-pro/src/cdd_sync_pref_sorter.java` — read in full before writing.

**Action:** Port the `neworder.pref` alphabetical sort utility.

**Contract:**
- Function: `sort_crates(serato_path: str) -> None`
  - Deletes existing `neworder.pref` if present, then recreates it
  - Scans `<serato_path>/Subcrates/` for `*.crate` files (non-recursive — Java only reads top-level)
  - Crate names = filenames without `.crate` extension, sorted alphabetically (`sorted()`)
  - File format (UTF-16BE encoded — use `open(..., 'w', encoding='utf-16-be')`):
    ```
    [begin record]\n
    [crate]<crate_name>\n   (one per crate)
    [end record]\n
    ```
  - If Subcrates dir doesn't exist or is empty: log and return without creating the file
- Logging: use logger `"cdd_sync"`

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro && python3 -c "
import sys; sys.path.insert(0, 'python')
from sync.pref_sorter import sort_crates
import tempfile, os
from pathlib import Path

with tempfile.TemporaryDirectory() as serato:
    sub = Path(serato) / 'Subcrates'
    sub.mkdir()
    (sub / 'Zoo.crate').write_bytes(b'')
    (sub / 'Alpha.crate').write_bytes(b'')
    (sub / 'Mango.crate').write_bytes(b'')

    sort_crates(serato)

    pref = Path(serato) / 'neworder.pref'
    assert pref.exists(), 'neworder.pref not created'
    content = pref.read_bytes().decode('utf-16-be')
    lines = content.strip().split('\n')
    assert lines[0] == '[begin record]', f'Bad header: {lines[0]}'
    assert lines[-1] == '[end record]', f'Bad footer: {lines[-1]}'
    crate_lines = [l for l in lines if l.startswith('[crate]')]
    names = [l[len('[crate]'):] for l in crate_lines]
    assert names == ['Alpha', 'Mango', 'Zoo'], f'Wrong order: {names}'

print('Phase 5 pref_sorter: ALL PASS')
"
```
Expected: `Phase 5 pref_sorter: ALL PASS`

---

## Phase 6 — Database Fixer (`python/sync/database_fixer.py`)

**File(s):** `python/sync/database_fixer.py`

**Java reference:** `java/shared/src/cdd_sync_database_fixer.java` — read in full before writing.

**Action:** Port the database path-patching utility. This rewrites `pfil` (path) entries in the binary `database V2` file for moved/renamed tracks.

**Contract:**
- Function: `update_paths(database_path: str, path_fixes: dict[str, str]) -> int`
  - Returns: count of paths actually updated
  - Opens the `database V2` binary file, does a single-pass TLV walk
  - For each `otrk` block, walks its inner TLV to find `pfil`
  - If `pfil` value (decoded UTF-16BE) matches a key in `path_fixes`: replace with the new value (encoded UTF-16BE), update the inner `pfil` block length, update the outer `otrk` block length accordingly
  - Writes the patched bytes back to the same file (in-place — not atomic, matches Java behaviour)
  - Use `core.binary_utils` for `read_big_endian_int`, `decode_utf16be`, `encode_utf16be`
  - On `IOError`: log and return 0
- Logging: use logger `"cdd_sync"`

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro && python3 -c "
import sys; sys.path.insert(0, 'python')
from sync.database_fixer import update_paths
from core.serato_parser import SeratoDatabase
import tempfile, io, struct
from pathlib import Path
from core.binary_utils import encode_utf16be

# Build minimal database V2 with one otrk/pfil entry
def build_db(path_val):
    buf = io.BytesIO()
    buf.write(b'vrsn\x00\x00')
    buf.write(encode_utf16be('2.0'))
    buf.write(encode_utf16be('/Serato ScratchLive Database'))
    path_bytes = encode_utf16be(path_val)
    pfil = b'pfil' + struct.pack('>I', len(path_bytes)) + path_bytes
    otrk = b'otrk' + struct.pack('>I', len(pfil)) + pfil
    buf.write(otrk)
    return buf.getvalue()

with tempfile.NamedTemporaryFile(delete=False, suffix='database V2') as f:
    tmp = Path(f.name)
tmp.write_bytes(build_db('Old/Path/track.mp3'))

count = update_paths(str(tmp), {'Old/Path/track.mp3': 'New/Path/track.mp3'})
assert count == 1, f'Expected 1, got {count}'

db2 = SeratoDatabase.read_from(tmp)
paths = db2.get_all_track_paths()
assert any('New/Path/track.mp3' in p for p in paths), f'Path not updated: {paths}'
tmp.unlink()

print('Phase 6 database_fixer: ALL PASS')
"
```
Expected: `Phase 6 database_fixer: ALL PASS`

---

## Phase 7 — Pipeline Orchestrator (`python/sync/pipeline.py`)

**File(s):** `python/sync/pipeline.py`

**Java reference:** `java/cdd-sync-pro/src/cdd_sync_main.java` (`runSync` method) and `java/cdd-sync-pro/src/cdd_sync_crate_fixer.java` — read both in full before writing.

**Action:** Port the full 4-step sync engine plus Step 0 (dupe management). This is the largest module. Wire all previously ported modules together.

**Contract:**
- Function: `run_sync(config, log_callback=None) -> None`
  - `config`: `SyncConfig` instance
  - `log_callback`: optional `Callable[[str], None]` — called with each log message (for GUI streaming). If `None`, log only to `logging`.
  - Execution order mirrors `cdd_sync_main.runSync()` exactly:
    1. Set log dir to `<volume_root>/cdd-sync-pro/logs/` (create if needed)
    2. Log: `"cdd-sync-pro started"`
    3. **Pre: Backup** — if `config.backup_enabled`: call `create_backup(config.serato_library_path)`. On `None` return: log error `"Backup failed. Aborting sync for safety."` and return.
    4. **Scan library** — `MediaLibrary.read_from(config.music_library_path)`. If `total_tracks() == 0`: log error and return.
    5. **Step 0 (early): Dupe move** — if `config.step0_enabled and config.dupe_move_enabled`: `scan_and_move_duplicates(...)`. If any moved: update DB with `update_paths(...)`, rescan library.
    6. Validate `serato_library_path` exists — if not, create with `os.makedirs` (confirm not needed in CLI; just create).
    7. Load database: `SeratoDatabase.read_from(db_path)` — catch exception, set `database = None` with info log.
    8. Validate `parent_crate_path` — create empty parent `.crate` if missing (use `write_crate`), check for duplicate parent crate name (abort if `> 1`).
    9. Load track index (database reference for path encoding): just use the already-loaded `database`.
    10. **Clear library** — if `config.clear_library_before_sync`: delete `Crates/`, `Subcrates/`, `database V2`.
    11. **Step 1** (`config.step1_enabled and not clear`): `update_database_paths(serato_path, library)`
    12. **Step 2** (`config.step2_enabled and not clear`): `fix_existing_crates(serato_path, library, database)`
    13. **Step 3** (`config.step3_enabled`): `append_new_tracks(serato_path, library, parent_crate_path, database)`
    14. **Step 4** (`config.step4_enabled`): `create_new_crates(serato_path, library, parent_crate_path, database)`
    15. **Step 0 (late)**: log-only dupe scan if `dupe_scan_enabled and not dupe_move_enabled and step0_enabled`
    16. Log: `"Sync Complete"`
    17. **Post: Sort crates** — if `config.crate_sorting_enabled`: `sort_crates(serato_path)`
    18. Log: `"[DRY RUN] Sync complete — no files were written."` if dry run, else `"Success."`
  - All write operations must be guarded: if `config.dry_run`, log `[DRY RUN] Would have: <description>` and skip the write.
- Helper functions within `pipeline.py` (ported from `cdd_sync_crate_fixer.java`):
  - `update_database_paths(serato_path: str, library) -> None` — Step 1
  - `fix_existing_crates(serato_path: str, library, database) -> None` — Step 2
  - `append_new_tracks(serato_path: str, library, parent_crate_path: Optional[str], database) -> None` — Step 3
  - `create_new_crates(serato_path: str, library, parent_crate_path: Optional[str], database) -> None` — Step 4
  - `build_library_index(library) -> dict[str, list[str]]` — filename → list of abs paths
  - `build_crate_file_map(library, parent_crate_path: Optional[str]) -> dict[str, list[str]]` — crate filename → track list (uses `%%` join convention from Java)
  - `collect_crate_files(directory: Path) -> list[Path]` — recursive `.crate` collector
  - `get_volume_root(serato_path: str) -> Optional[str]` — parent of `_Serato_` dir, or `None`
- Crate filename convention: `<parent>%%<child>.crate` using `%%` as separator (exact Java match)

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro && python3 -c "
import sys; sys.path.insert(0, 'python')
from sync.pipeline import build_library_index, build_crate_file_map, collect_crate_files
from sync.media_library import MediaLibrary
import tempfile, os
from pathlib import Path

with tempfile.TemporaryDirectory() as vol:
    root = Path(vol) / 'music'
    a = root / 'A'; a.mkdir(parents=True)
    (a / 'track1.mp3').write_bytes(b'x')
    (a / 'track2.flac').write_bytes(b'x')

    lib = MediaLibrary.read_from(str(root))

    idx = build_library_index(lib)
    assert 'track1.mp3' in idx, f'track1 not in index: {list(idx.keys())}'
    assert 'track2.flac' in idx

    crate_map = build_crate_file_map(lib, None)
    assert 'A.crate' in crate_map, f'A.crate not in map: {list(crate_map.keys())}'
    assert len(crate_map['A.crate']) == 2

    serato = Path(vol) / '_Serato_'
    sub = serato / 'Subcrates'; sub.mkdir(parents=True)
    (sub / 'foo.crate').write_bytes(b'')
    (sub / 'bar.crate').write_bytes(b'')
    crates = collect_crate_files(sub)
    assert len(crates) == 2, f'Expected 2 crates, got {len(crates)}'

print('Phase 7 pipeline helpers: ALL PASS')
"
```
Expected: `Phase 7 pipeline helpers: ALL PASS`

---

## Phase 8 — CLI Entrypoint + Integration Tests

**File(s):** `python/main.py`, `python/tests/test_pipeline.py`

**Action:** Create the CLI entrypoint and integration tests that run a full dry-run sync against a temp fixture.

**`python/main.py` contract:**
- `python3 python/main.py` launches the sync (CLI mode only — GUI deferred)
- Parses `--dry-run` flag (same as Java)
- Loads `config = SyncConfig.load()` — exits with human-readable message if `config.yaml` not found
- Calls `run_sync(config)`
- Exits 0 on success, 1 on fatal error

**`python/tests/test_pipeline.py` contract** — 4 tests:
1. `test_dry_run_no_writes` — dry-run sync on a real temp fixture: assert zero `.crate` files created, zero `database V2` changes
2. `test_step4_creates_crates` — non-dry-run sync on temp fixture with 2 subfolders: assert 2 `.crate` files created in `Subcrates/`
3. `test_step2_fixes_crate_path` — create a `.crate` with a broken path, run sync (steps 1+2 only enabled), assert the path is fixed
4. `test_crate_sorting_enabled` — run sync with `crate_sorting_enabled=True`, assert `neworder.pref` is created and sorted

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro/python && python3 -m pytest tests/test_pipeline.py -v 2>&1 | tail -15
```
Expected: `4 passed` in output.

---

## Phase 9 — AGENT_LOG + Commit

**File(s):** `md/AGENT_LOG.md`

**Action:** Append one AGENT_LOG entry (newest at top, after the comment line). Then stage and commit.

**Entry format:**
```markdown
## 2026-04-02 — Python Pipeline Implementation (py-pipeline)

- **Task**: Port sync pipeline from Java to Python: config, media library, backup, dupe mover, pref sorter, pipeline orchestrator, CLI entrypoint
- **Files Changed**:
  - `python/config.py` [NEW]
  - `python/config.template.yaml` [NEW]
  - `python/sync/__init__.py` [NEW]
  - `python/sync/media_library.py` [NEW]
  - `python/sync/backup.py` [NEW]
  - `python/sync/dupe_mover.py` [NEW]
  - `python/sync/pref_sorter.py` [NEW]
  - `python/sync/database_fixer.py` [NEW]
  - `python/sync/pipeline.py` [NEW]
  - `python/main.py` [NEW]
  - `python/tests/test_pipeline.py` [NEW]
  - `md/AGENT_LOG.md` [MODIFIED]
- **What Was Done**: Implemented all pipeline phases. Full dry-run parity gate passed. java/ untouched.
- **Docs to Update**: None — done here
```

**Commit command:**
```bash
cd /Users/culprit/Git/cdd-sync-pro
git add python/ md/AGENT_LOG.md
git commit -m "feat(python): sync pipeline — config, media library, backup, dupe mover, pref sorter, orchestrator, CLI"
git push origin python
```

**Verify:**
```bash
git log --oneline -1
```
Expected: top commit contains `feat(python): sync pipeline`

---

## Done

All phases complete when:

- [ ] `python3 -m pytest tests/ -v` reports all PASSED (≥ 21 tests — 17 existing + 4 new)
- [ ] `python3 python/main.py --dry-run` runs without error when `config.yaml` exists
- [ ] `git log --oneline -1` shows the `feat(python): sync pipeline` commit on branch `python`
- [ ] `java/` directory completely unmodified — `git diff java/` is empty
