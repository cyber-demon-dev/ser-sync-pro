# Phase 7 Executor — cdd-sync-pro / py-pipeline

> **Plan:** `md/actions/py-pipeline/PLAN.md`
> **Phase:** 7 — Pipeline Orchestrator (`python/sync/pipeline.py`)
> **File(s):** `python/sync/pipeline.py`

## Your Only Job

Execute **Phase 7 only**. Stop after Verify passes.

---

## Mandatory Pre-Flight

| Check | Expected | STOP condition |
|-------|----------|----------------|
| `pwd` | `/Users/culprit/Git/cdd-sync-pro` | Wrong dir → STOP |
| `git status` | clean working tree | Uncommitted changes → STOP |
| `git branch` | `python` | Wrong branch → STOP |
| `python/sync/database_fixer.py` exists | present | Missing → STOP (Phase 6 not done) |
| `md/actions/py-pipeline/PLAN.md` | present | Missing → STOP |

---

## Execution

**Step 1:** Read `java/cdd-sync-pro/src/cdd_sync_main.java` — specifically `runSync()` method — in full.

**Step 2:** Read `java/cdd-sync-pro/src/cdd_sync_crate_fixer.java` in full.

**Step 3:** Read Phase 7 in `md/actions/py-pipeline/PLAN.md` in full. The contract is exhaustive — follow it exactly.

**Step 4:** Create `python/sync/pipeline.py` with:
- `run_sync(config, log_callback=None)` — the main orchestrator
- Helper functions: `update_database_paths`, `fix_existing_crates`, `append_new_tracks`, `create_new_crates`, `build_library_index`, `build_crate_file_map`, `collect_crate_files`, `get_volume_root`
- Imports: use only modules already in `python/` (`config`, `sync.backup`, `sync.dupe_mover`, `sync.pref_sorter`, `sync.database_fixer`, `sync.media_library`, `core.serato_parser`, `core.path_utils`, `core.binary_utils`)
- Crate filename separator is `%%` (two percent signs — the exact Java convention)
- All write operations skip when `config.dry_run is True` and log `[DRY RUN] Would have: <description>`
- Use logger `"cdd_sync"` for all log calls

**Critical rules (mirror Java exactly):**
- Step 2 (`fix_existing_crates`): uses `build_library_index` (filesystem, not DB) as source of truth; if multiple candidates for same filename → use first (no skip)
- Step 3 (`append_new_tracks`): reads existing crate with `read_crate`, calls `crate.add_track()` for each new track (dedup via `_dedup_set`), writes back only if track count increased
- Step 4 (`create_new_crates`): creates crate file only if it does NOT already exist; uses `crate.add_tracks()` for a fresh crate

---

## Verify

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

## Audit Update (before reporting)

1. Open `md/actions/py-pipeline/AUDIT.md` → Phase 7 row → `✅ DONE`
2. Fill `Verify Output` and `Pass/Fail`

---

## Report

```
Phase: 7
File: python/sync/pipeline.py
Verify output: [raw output]
Status: PASS / DEVIATION
Deviation: [description, or NONE]
```

**You are done. Stop here. Do not read the next phase. Do not continue.**
