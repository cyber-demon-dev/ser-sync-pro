# Phase 2 Executor — cdd-sync-pro / py-pipeline

> **Plan:** `md/actions/py-pipeline/PLAN.md`
> **Phase:** 2 — Media Library Scanner (`python/sync/media_library.py`)
> **File(s):** `python/sync/__init__.py`, `python/sync/media_library.py`

## Your Only Job

Execute **Phase 2 only**. Stop after Verify passes.

---

## Mandatory Pre-Flight

| Check | Expected | STOP condition |
|-------|----------|----------------|
| `pwd` | `/Users/culprit/Git/cdd-sync-pro` | Wrong dir → STOP |
| `git status` | clean working tree | Uncommitted changes → STOP |
| `git branch` | `python` | Wrong branch → STOP |
| `python/config.py` exists | present | Missing → STOP (Phase 1 not done) |
| `md/actions/py-pipeline/PLAN.md` | present | Missing → STOP |

---

## Execution

**Step 1:** Read `java/shared/src/cdd_sync_media_library.java` in full. It is your specification.

**Step 2:** Read Phase 2 in `md/actions/py-pipeline/PLAN.md` in full.

**Step 3:** Create `python/sync/__init__.py` (empty — package marker).

**Step 4:** Create `python/sync/media_library.py` implementing `MediaLibrary` per the contract. Key requirements:
- `MEDIA_EXTENSIONS` frozenset matches Java's set exactly
- `read_from(path)` classmethod — recursive scan using `os.scandir`
- Track paths resolved via `Path(f).resolve()` (mirrors Java `toRealPath()`)
- Children sorted alphabetically by `directory` name
- Parallel scanning with `ThreadPoolExecutor(max_workers=min(4, os.cpu_count()))` when subdirs > 1
- `flatten_tracks()` returns depth-first flat list
- `remove_tracks(paths)` removes from self and all children recursively

---

## Verify

```bash
cd /Users/culprit/Git/cdd-sync-pro && python3 -c "
import sys; sys.path.insert(0, 'python')
from sync.media_library import MediaLibrary
import tempfile, os
from pathlib import Path

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

## Audit Update (before reporting)

1. Open `md/actions/py-pipeline/AUDIT.md` → Phase 2 row → `✅ DONE`
2. Fill `Verify Output` and `Pass/Fail`

---

## Report

```
Phase: 2
File: python/sync/__init__.py, python/sync/media_library.py
Verify output: [raw output]
Status: PASS / DEVIATION
Deviation: [description, or NONE]
```

**You are done. Stop here. Do not read the next phase. Do not continue.**
