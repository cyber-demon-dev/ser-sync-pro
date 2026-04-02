# Phase 4 Executor — cdd-sync-pro / py-pipeline

> **Plan:** `md/actions/py-pipeline/PLAN.md`
> **Phase:** 4 — Dupe Mover (`python/sync/dupe_mover.py`)
> **File(s):** `python/sync/dupe_mover.py`

## Your Only Job

Execute **Phase 4 only**. Stop after Verify passes.

---

## Mandatory Pre-Flight

| Check | Expected | STOP condition |
|-------|----------|----------------|
| `pwd` | `/Users/culprit/Git/cdd-sync-pro` | Wrong dir → STOP |
| `git status` | clean working tree | Uncommitted changes → STOP |
| `git branch` | `python` | Wrong branch → STOP |
| `python/sync/backup.py` exists | present | Missing → STOP (Phase 3 not done) |
| `md/actions/py-pipeline/PLAN.md` | present | Missing → STOP |

---

## Execution

**Step 1:** Read `java/cdd-sync-pro/src/cdd_sync_dupe_mover.java` in full.

**Step 2:** Read Phase 4 in `md/actions/py-pipeline/PLAN.md` in full.

**Step 3:** Create `python/sync/dupe_mover.py` implementing `scan_and_move_duplicates(...)` per the contract. Key requirements:
- Return early with `{}` when `detection_mode == "off"`
- Key grouping: `"name-and-size"` → `filename.lower() + "|" + str(file.stat().st_size)`, `"name-only"` → `filename.lower()`
- Sort by `mtime`: `"keep-newest"` → descending (newest first → index 0 is kept), `"keep-oldest"` → ascending
- Timestamp folder: `datetime.now().strftime("%Y-%m-%d_%H-%M-%S")`
- Dupes root: `Path(music_library_root).parent / "cdd-sync-pro/dupes" / timestamp`
- Use `shutil.move(src, dst)` — create parent dirs first with `os.makedirs(exist_ok=True)`
- Write `dupes.log` inside the dupes folder matching Java format (see `writeLogFile()` in Java source)
- Use logger `"cdd_sync"` for all logging

---

## Verify

```bash
cd /Users/culprit/Git/cdd-sync-pro && python3 -c "
import sys; sys.path.insert(0, 'python')
from sync.dupe_mover import scan_and_move_duplicates
from sync.media_library import MediaLibrary
import tempfile, os, time
from pathlib import Path

with tempfile.TemporaryDirectory() as vol:
    root = Path(vol) / 'music'
    a = root / 'A'; a.mkdir(parents=True)
    b = root / 'B'; b.mkdir(parents=True)
    (a / 'track.mp3').write_bytes(b'x' * 100)
    time.sleep(0.05)
    (b / 'track.mp3').write_bytes(b'x' * 100)

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

## Audit Update (before reporting)

1. Open `md/actions/py-pipeline/AUDIT.md` → Phase 4 row → `✅ DONE`
2. Fill `Verify Output` and `Pass/Fail`

---

## Report

```
Phase: 4
File: python/sync/dupe_mover.py
Verify output: [raw output]
Status: PASS / DEVIATION
Deviation: [description, or NONE]
```

**You are done. Stop here. Do not read the next phase. Do not continue.**
