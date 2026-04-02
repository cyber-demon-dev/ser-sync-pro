# Phase 3 Executor — cdd-sync-pro / py-pipeline

> **Plan:** `md/actions/py-pipeline/PLAN.md`
> **Phase:** 3 — Backup (`python/sync/backup.py`)
> **File(s):** `python/sync/backup.py`

## Your Only Job

Execute **Phase 3 only**. Stop after Verify passes.

---

## Mandatory Pre-Flight

| Check | Expected | STOP condition |
|-------|----------|----------------|
| `pwd` | `/Users/culprit/Git/cdd-sync-pro` | Wrong dir → STOP |
| `git status` | clean working tree | Uncommitted changes → STOP |
| `git branch` | `python` | Wrong branch → STOP |
| `python/sync/media_library.py` exists | present | Missing → STOP (Phase 2 not done) |
| `md/actions/py-pipeline/PLAN.md` | present | Missing → STOP |

---

## Execution

**Step 1:** Read `java/shared/src/cdd_sync_backup.java` in full.

**Step 2:** Read Phase 3 in `md/actions/py-pipeline/PLAN.md` in full.

**Step 3:** Create `python/sync/backup.py` implementing `create_backup(serato_path)` per the contract. Key requirements:
- Backup root: `<serato_parent>/cdd-sync-pro/backup/`
- Backup name: `<epoch_ms>_Serato_` (use `int(time.time() * 1000)`)
- Use `shutil.copytree(src, dst, copy_function=shutil.copy2)` — does NOT accept `dirs_exist_ok=False` by default, so `dst` must not exist
- Import `format_size` from `core.binary_utils` — do NOT reimplement it
- Use `logging.getLogger("cdd_sync")` for all log calls
- Return backup dir path on success, `None` on any failure

---

## Verify

```bash
cd /Users/culprit/Git/cdd-sync-pro && python3 -c "
import sys; sys.path.insert(0, 'python')
from sync.backup import create_backup
import tempfile, os
from pathlib import Path

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

## Audit Update (before reporting)

1. Open `md/actions/py-pipeline/AUDIT.md` → Phase 3 row → `✅ DONE`
2. Fill `Verify Output` and `Pass/Fail`

---

## Report

```
Phase: 3
File: python/sync/backup.py
Verify output: [raw output]
Status: PASS / DEVIATION
Deviation: [description, or NONE]
```

**You are done. Stop here. Do not read the next phase. Do not continue.**
