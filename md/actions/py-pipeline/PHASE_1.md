# Phase 1 Executor — cdd-sync-pro / py-pipeline

> **Plan:** `md/actions/py-pipeline/PLAN.md`
> **Phase:** 1 — Config (`python/config.py`)
> **File(s):** `python/config.py`, `python/config.template.yaml`

## Your Only Job

Execute **Phase 1 only**. Stop after Verify passes.

---

## Mandatory Pre-Flight

| Check | Expected | STOP condition |
|-------|----------|----------------|
| `pwd` | `/Users/culprit/Git/cdd-sync-pro` | Wrong dir → STOP |
| `git status` | clean working tree | Uncommitted changes → STOP |
| `git branch` | `python` | Wrong branch → STOP |
| `python/core/path_utils.py` exists | present | Missing → STOP |
| `md/actions/py-pipeline/PLAN.md` | present | Missing → STOP |

---

## Execution

**Step 1:** Read `java/cdd-sync-pro/src/cdd_sync_config.java` in full. It is your specification.

**Step 2:** Read Phase 1 in `md/actions/py-pipeline/PLAN.md` in full. Note every attribute, default value, YAML key name, and validation rule.

**Step 3:** Create `python/config.py` implementing `SyncConfig` per the contract in PLAN.md Phase 1. Key requirements:
- Dataclass or plain class with all attributes and defaults listed in the plan
- `SyncConfig.load(path)` classmethod reads YAML via `pyyaml`
- `save(path)` writes YAML via `pyyaml`
- `parent_crate_path` setter/validator raises `ValueError` for values containing `%%`
- `dupe_move_enabled` computed property
- Required fields (`music_library_path`, `serato_library_path`) raise `ValueError` if empty/missing

**Step 4:** Create `python/config.template.yaml` — commented template showing all keys with their defaults.

---

## Verify

Run the exact verify command from Phase 1 of PLAN.md:

```bash
cd /Users/culprit/Git/cdd-sync-pro && python3 -c "
import sys; sys.path.insert(0, 'python')
from config import SyncConfig
from pathlib import Path
import tempfile, yaml, os

cfg = SyncConfig(music_library_path='/tmp/music', serato_library_path='/tmp/serato')
assert cfg.backup_enabled == True
assert cfg.dry_run == False
assert cfg.dupe_move_enabled == False

with tempfile.NamedTemporaryFile(suffix='.yaml', delete=False, mode='w') as f:
    tmp = Path(f.name)
cfg.save(tmp)
cfg2 = SyncConfig.load(tmp)
assert cfg2.music_library_path == '/tmp/music'
assert cfg2.backup_enabled == True
tmp.unlink()

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

## Audit Update (before reporting)

1. Open `md/actions/py-pipeline/AUDIT.md`
2. Find Phase 1 row → set `Status` → `✅ DONE`
3. Fill `Verify Output` with actual output
4. Set `Pass/Fail` → ✅ PASS or ⚠️ DEVIATION

---

## Report

```
Phase: 1
File: python/config.py, python/config.template.yaml
Verify output: [raw output]
Status: PASS / DEVIATION
Deviation: [description, or NONE]
```

**You are done. Stop here. Do not read the next phase. Do not continue.**
