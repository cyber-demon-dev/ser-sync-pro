# Python Pipeline — Plan

> Feature: `py-pipeline`
> Scope: Port the sync pipeline from Java to Python — config, media library, backup, dupe mover, pref sorter, database fixer, 4-step orchestrator, and CLI entrypoint.
> Execute with: `/run-phase py-pipeline`

## Context

| Item | Value |
|------|-------|
| Project root | `/Users/culprit/Git/cdd-sync-pro` |
| Branch | `python` |
| Runtime | Python 3.12+ |
| Test command | `cd python && python3 -m pytest tests/ -v` |
| Key files | `python/core/`, `python/sync/`, `python/config.py`, `python/main.py` |
| Current state | `python/` has `core/` (path_utils, binary_utils, serato_parser) and `tests/` (17 passing). All sync modules are missing. |

---

## Phase 1 — Config

**File(s):** `python/config.py`, `python/config.template.yaml`
**Reference:** `java/cdd-sync-pro/src/cdd_sync_config.java`

**Intent:** Create a `SyncConfig` dataclass that loads from and saves to YAML. Replace Java's `.properties` format with YAML, mapping every Java getter to a Python attribute with the same default value. Required fields (`music_library_path`, `serato_library_path`) must raise `ValueError` if absent. The `parent_crate_path` field must reject values containing `%%`. Add a `dupe_move_enabled` computed property. Also create a `config.template.yaml` with commented groups (Paths, Options, Deduplication, Pipeline Steps).

**Constraints:**
- Class name: `SyncConfig`. Factory: `SyncConfig.load(path)`. Persister: `save(path)`.
- Exact YAML keys: `music_library_path`, `serato_library_path`, `parent_crate_path`, `clear_library_before_sync`, `backup_enabled`, `dupe_scan_enabled`, `dupe_move_mode`, `dupe_detection_mode`, `crate_sorting_enabled`, `step0_enabled`…`step4_enabled`, `dry_run`.
- Read the Java source for exact default values before writing.

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro && python3 -c "
import sys; sys.path.insert(0, 'python')
from config import SyncConfig
from pathlib import Path
import tempfile

cfg = SyncConfig(music_library_path='/tmp/music', serato_library_path='/tmp/serato')
assert cfg.backup_enabled == True
assert cfg.dry_run == False
assert cfg.dupe_move_enabled == False

with tempfile.NamedTemporaryFile(suffix='.yaml', delete=False) as f:
    tmp = Path(f.name)
cfg.save(tmp); cfg2 = SyncConfig.load(tmp)
assert cfg2.music_library_path == '/tmp/music'
tmp.unlink()

try:
    SyncConfig(music_library_path='/x', serato_library_path='/y', parent_crate_path='A%%B')
    assert False
except ValueError:
    pass

print('Phase 1: ALL PASS')
"
```

⛔ STOP — Report result. Wait for user.

---

## Phase 2 — Media Library + Backup

**File(s):** `python/sync/__init__.py`, `python/sync/media_library.py`, `python/sync/backup.py`
**Reference:** `java/shared/src/cdd_sync_media_library.java`, `java/shared/src/cdd_sync_backup.java`

**Intent:**
- **MediaLibrary**: Recursive scanner using `os.scandir` + `ThreadPoolExecutor` (max 4 workers) for parallel subdir scanning. Tracks resolved to real paths. `MEDIA_EXTENSIONS` frozenset matches Java exactly. Implement `read_from`, `total_tracks`, `total_directories`, `flatten_tracks`, `remove_tracks`. Children sorted alphabetically.
- **Backup**: `create_backup(serato_path)` copies the `_Serato_` folder into `<volume_root>/cdd-sync-pro/backup/<epoch_ms>_Serato_` using `shutil.copytree` with `copy2`. Returns absolute backup path or `None` on failure. Import `format_size` from `core.binary_utils` — do not duplicate it.
- All log calls use logger name `"cdd_sync"`.

**Constraints:**
- Parallel scan only when `len(subdirs) > 1`.
- Backup root is a sibling of `_Serato_`, not inside it.
- `create_backup` must log start and completion messages.

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro && python3 -c "
import sys; sys.path.insert(0, 'python')
from sync.media_library import MediaLibrary
from sync.backup import create_backup
import tempfile, os
from pathlib import Path

with tempfile.TemporaryDirectory() as root:
    os.makedirs(f'{root}/A'); os.makedirs(f'{root}/B')
    Path(f'{root}/A/track1.mp3').write_bytes(b'x')
    Path(f'{root}/A/track2.flac').write_bytes(b'x')
    Path(f'{root}/B/track3.mp3').write_bytes(b'x')
    lib = MediaLibrary.read_from(root)
    assert lib.total_tracks() == 3
    assert lib.total_directories() == 2

with tempfile.TemporaryDirectory() as vol:
    serato = Path(vol) / '_Serato_'; serato.mkdir()
    (serato / 'database V2').write_bytes(b'vrsn\x00\x00')
    result = create_backup(str(serato))
    assert result and Path(result).exists()

print('Phase 2: ALL PASS')
"
```

⛔ STOP — Report result. Wait for user.

---

## Phase 3 — Dupe Mover + Pref Sorter

**File(s):** `python/sync/dupe_mover.py`, `python/sync/pref_sorter.py`
**Reference:** `java/cdd-sync-pro/src/cdd_sync_dupe_mover.java`, `java/cdd-sync-pro/src/cdd_sync_pref_sorter.java`

**Intent:**
- **DupeMover**: `scan_and_move_duplicates(music_library_root, library, detection_mode, move_mode)` returns `{moved_path: kept_path}`. Detection keys: `"name-and-size"` = `filename.lower() + "|" + str(size)`, `"name-only"` = `filename.lower()`. `"off"` returns immediately. Move mode determines which file is kept (newest or oldest by mtime). Dupes folder: `<library_parent>/cdd-sync-pro/dupes/<timestamp>/`. Write `dupes.log` matching Java's `writeLogFile()` format. Use `shutil.move`, skip on `OSError`.
- **PrefSorter**: `sort_crates(serato_path)` deletes and recreates `neworder.pref` in UTF-16BE. Scans top-level `Subcrates/` only. File format: `[begin record]\n[crate]<name>\n...[end record]\n`. No-op if Subcrates missing or empty.
- Both use logger `"cdd_sync"`.

**Constraints:**
- Dupe timestamp format: `datetime.now().strftime("%Y-%m-%d_%H-%M-%S")`.
- `neworder.pref` encoding must be exact UTF-16BE (no BOM) — use `open(..., 'w', encoding='utf-16-be')`.

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro && python3 -c "
import sys; sys.path.insert(0, 'python')
from sync.dupe_mover import scan_and_move_duplicates
from sync.media_library import MediaLibrary
from sync.pref_sorter import sort_crates
import tempfile, os, time
from pathlib import Path

with tempfile.TemporaryDirectory() as vol:
    root = Path(vol) / 'music'
    (root / 'A').mkdir(parents=True); (root / 'B').mkdir(parents=True)
    (root / 'A' / 'track.mp3').write_bytes(b'x' * 100)
    time.sleep(0.05)
    (root / 'B' / 'track.mp3').write_bytes(b'x' * 100)
    lib = MediaLibrary.read_from(str(root))
    moved = scan_and_move_duplicates(str(root), lib, 'name-and-size', 'keep-newest')
    assert len(moved) == 1

with tempfile.TemporaryDirectory() as serato:
    sub = Path(serato) / 'Subcrates'; sub.mkdir()
    (sub / 'Zoo.crate').write_bytes(b'')
    (sub / 'Alpha.crate').write_bytes(b'')
    sort_crates(serato)
    pref = Path(serato) / 'neworder.pref'
    assert pref.exists()
    content = pref.read_bytes().decode('utf-16-be')
    names = [l[7:] for l in content.strip().split('\n') if l.startswith('[crate]')]
    assert names == ['Alpha', 'Zoo']

print('Phase 3: ALL PASS')
"
```

⛔ STOP — Report result. Wait for user.

---

## Phase 4 — Database Fixer

**File(s):** `python/sync/database_fixer.py`
**Reference:** `java/shared/src/cdd_sync_database_fixer.java`

**Intent:** `update_paths(database_path, path_fixes)` does a single-pass TLV walk of the binary `database V2` file. For each `otrk` block, walk its inner TLVs to find `pfil`. If the UTF-16BE decoded path matches a key in `path_fixes`, replace it with the new value (UTF-16BE encoded), update inner `pfil` block length and outer `otrk` block length accordingly. Write patched bytes back in-place. Return count of paths updated. Use `core.binary_utils` for TLV helpers — do not duplicate them. Return 0 and log on `IOError`.

**Constraints:**
- In-place write (not atomic) — matches Java behaviour.
- Logger: `"cdd_sync"`.
- Use `read_big_endian_int`, `decode_utf16be`, `encode_utf16be` from `core.binary_utils`.

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro && python3 -c "
import sys; sys.path.insert(0, 'python')
from sync.database_fixer import update_paths
from core.serato_parser import SeratoDatabase
from core.binary_utils import encode_utf16be
import tempfile, struct
from pathlib import Path

def build_db(path_val):
    import io
    buf = io.BytesIO()
    buf.write(b'vrsn\x00\x00')
    buf.write(encode_utf16be('2.0'))
    buf.write(encode_utf16be('/Serato ScratchLive Database'))
    path_bytes = encode_utf16be(path_val)
    pfil = b'pfil' + struct.pack('>I', len(path_bytes)) + path_bytes
    buf.write(b'otrk' + struct.pack('>I', len(pfil)) + pfil)
    return buf.getvalue()

with tempfile.NamedTemporaryFile(delete=False, suffix='database V2') as f:
    tmp = Path(f.name)
tmp.write_bytes(build_db('Old/Path/track.mp3'))
count = update_paths(str(tmp), {'Old/Path/track.mp3': 'New/Path/track.mp3'})
assert count == 1, f'Expected 1, got {count}'
db2 = SeratoDatabase.read_from(tmp)
assert any('New/Path/track.mp3' in p for p in db2.get_all_track_paths())
tmp.unlink()
print('Phase 4: ALL PASS')
"
```

⛔ STOP — Report result. Wait for user.

---

## Phase 5 — Pipeline Orchestrator, CLI, and Integration Tests

**File(s):** `python/sync/pipeline.py`, `python/main.py`, `python/tests/test_pipeline.py`
**Reference:** `java/cdd-sync-pro/src/cdd_sync_main.java` (`runSync` method), `java/cdd-sync-pro/src/cdd_sync_crate_fixer.java`

**Intent:** Wire all previously ported modules into a `run_sync(config, log_callback=None)` function that executes the exact same sequence as `cdd_sync_main.runSync()`:
1. Set log dir → log start → backup (abort if fails) → scan library (abort if 0 tracks)
2. Step 0 early: if dupe move enabled, scan+move dupes, update DB, rescan
3. Validate/create `serato_library_path` → load database → validate parent crate → load track index
4. Clear library if configured
5. Steps 1–4 (each guarded by its `config.stepN_enabled` flag)
6. Step 0 late: log-only dupe scan
7. Sort crates if enabled → log completion

All write operations must be guarded by `config.dry_run`. Helper functions live in `pipeline.py`: `update_database_paths`, `fix_existing_crates`, `append_new_tracks`, `create_new_crates`, `build_library_index`, `build_crate_file_map`, `collect_crate_files`, `get_volume_root`. Crate filename convention: `<parent>%%<child>.crate`.

`main.py`: parse `--dry-run` flag, `SyncConfig.load()`, call `run_sync`, exit 0/1.

Integration tests (4): dry-run no-writes, step4 creates crates, step2 fixes broken path, crate sorting creates sorted `neworder.pref`.

**Constraints:**
- `log_callback` is optional — if None, only log to `logging`.
- `get_volume_root` returns parent dir of `_Serato_`, or `None`.
- Read both Java reference files before writing.

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro/python && python3 -m pytest tests/test_pipeline.py -v 2>&1 | tail -15
```
Expected: `4 passed` in output.

⛔ STOP — Report result. Wait for user.

---

## Phase 6 — Commit

**Action:** Append one entry to `md/AGENT_LOG.md` (newest at top, after the comment line). Then stage and commit.

**AGENT_LOG entry format:**
```
## 2026-04-02 — Python Pipeline Implementation (py-pipeline)
- Task: Port sync pipeline from Java to Python
- Files Changed: python/config.py [NEW], python/config.template.yaml [NEW], python/sync/__init__.py [NEW], python/sync/media_library.py [NEW], python/sync/backup.py [NEW], python/sync/dupe_mover.py [NEW], python/sync/pref_sorter.py [NEW], python/sync/database_fixer.py [NEW], python/sync/pipeline.py [NEW], python/main.py [NEW], python/tests/test_pipeline.py [NEW], md/AGENT_LOG.md [MODIFIED]
- What Was Done: Implemented all pipeline phases. Full integration test suite passing. java/ untouched.
- Docs to Update: None
```

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

⛔ STOP — Report result. Wait for user.

---

## Done

All phases complete when:

- [ ] `python3 -m pytest tests/ -v` reports ≥ 21 passed (17 existing + 4 new)
- [ ] `python3 python/main.py --dry-run` exits 0 when `config.yaml` exists
- [ ] `git log --oneline -1` shows the `feat(python): sync pipeline` commit on branch `python`
- [ ] `java/` directory completely unmodified — `git diff java/` is empty
