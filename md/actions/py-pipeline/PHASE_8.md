# Phase 8 Executor — cdd-sync-pro / py-pipeline

> **Plan:** `md/actions/py-pipeline/PLAN.md`
> **Phase:** 8 — CLI Entrypoint + Integration Tests
> **File(s):** `python/main.py`, `python/tests/test_pipeline.py`

## Your Only Job

Execute **Phase 8 only**. Stop after Verify passes.

---

## Mandatory Pre-Flight

| Check | Expected | STOP condition |
|-------|----------|----------------|
| `pwd` | `/Users/culprit/Git/cdd-sync-pro` | Wrong dir → STOP |
| `git status` | clean working tree | Uncommitted changes → STOP |
| `git branch` | `python` | Wrong branch → STOP |
| `python/sync/pipeline.py` exists | present | Missing → STOP (Phase 7 not done) |
| `md/actions/py-pipeline/PLAN.md` | present | Missing → STOP |

---

## Execution

**Step 1:** Read Phase 8 in `md/actions/py-pipeline/PLAN.md` in full.

**Step 2:** Create `python/main.py`:

```python
#!/usr/bin/env python3
"""
cdd-sync-pro CLI entrypoint.
Usage: python3 main.py [--dry-run]
"""
import sys
import logging
from pathlib import Path

logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')

def main():
    sys.path.insert(0, str(Path(__file__).parent))
    from config import SyncConfig
    from sync.pipeline import run_sync

    dry_run = '--dry-run' in sys.argv

    config_path = Path(__file__).parent / 'config.yaml'
    if not config_path.exists():
        print(f"ERROR: config.yaml not found at {config_path}")
        print("Copy config.template.yaml to config.yaml and fill in your paths.")
        sys.exit(1)

    try:
        config = SyncConfig.load(config_path)
    except ValueError as e:
        print(f"ERROR: Invalid config: {e}")
        sys.exit(1)

    if dry_run:
        config.dry_run = True

    try:
        run_sync(config)
    except Exception as e:
        logging.getLogger('cdd_sync').error(f"Fatal: {e}")
        sys.exit(1)

if __name__ == '__main__':
    main()
```

**Step 3:** Create `python/tests/test_pipeline.py` with the 4 integration tests specified in Phase 8 of PLAN.md. Each test builds a temporary fixture, runs `run_sync(config)`, and asserts the expected outcome. Use `pytest` fixtures for temp directories. Tests must not leave any files on disk after completion (use `pytest`'s `tmp_path` fixture).

The 4 tests (implement all four):

1. **`test_dry_run_no_writes`**: dry-run with 2 subfolders + 1 track each. Assert `Subcrates/` has zero `.crate` files created.

2. **`test_step4_creates_crates`**: non-dry-run with 2 subfolders (`FolderA/track.mp3`, `FolderB/track.flac`). Assert both crates created in `Subcrates/`, each with 1 track. Only `step4_enabled` (steps 0–3 disabled).

3. **`test_step2_fixes_crate_path`**: write a `.crate` file with a broken track path (`OldFolder/track.mp3`). Media library has `NewFolder/track.mp3` (same filename). Run with only step2 enabled. Assert crate now contains `NewFolder/track.mp3` (or normalized equivalent).

4. **`test_crate_sorting_enabled`**: non-dry-run with `crate_sorting_enabled=True`. Create 3 subfolders (Zoo, Alpha, Mango). Assert `neworder.pref` is created and crate order is `['Alpha', 'Mango', 'Zoo']`.

---

## Verify

```bash
cd /Users/culprit/Git/cdd-sync-pro/python && python3 -m pytest tests/test_pipeline.py -v 2>&1 | tail -15
```

Expected: `4 passed` in output. Zero failures, zero errors.

---

## Audit Update (before reporting)

1. Open `md/actions/py-pipeline/AUDIT.md` → Phase 8 row → `✅ DONE`
2. Fill `Verify Output` with pytest summary line
3. Set `Pass/Fail`

---

## Report

```
Phase: 8
File: python/main.py, python/tests/test_pipeline.py
Verify output: [pytest summary line]
Status: PASS / DEVIATION
Deviation: [description, or NONE]
```

**You are done. Stop here. Do not read the next phase. Do not continue.**
