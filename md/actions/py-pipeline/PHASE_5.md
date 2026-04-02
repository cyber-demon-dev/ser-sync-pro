# Phase 5 Executor — cdd-sync-pro / py-pipeline

> **Plan:** `md/actions/py-pipeline/PLAN.md`
> **Phase:** 5 — Pref Sorter (`python/sync/pref_sorter.py`)
> **File(s):** `python/sync/pref_sorter.py`

## Your Only Job

Execute **Phase 5 only**. Stop after Verify passes.

---

## Mandatory Pre-Flight

| Check | Expected | STOP condition |
|-------|----------|----------------|
| `pwd` | `/Users/culprit/Git/cdd-sync-pro` | Wrong dir → STOP |
| `git status` | clean working tree | Uncommitted changes → STOP |
| `git branch` | `python` | Wrong branch → STOP |
| `python/sync/dupe_mover.py` exists | present | Missing → STOP (Phase 4 not done) |
| `md/actions/py-pipeline/PLAN.md` | present | Missing → STOP |

---

## Execution

**Step 1:** Read `java/cdd-sync-pro/src/cdd_sync_pref_sorter.java` in full.

**Step 2:** Read Phase 5 in `md/actions/py-pipeline/PLAN.md` in full.

**Step 3:** Create `python/sync/pref_sorter.py` implementing `sort_crates(serato_path)` per the contract. Key requirements:
- Non-recursive scan of `<serato_path>/Subcrates/` for `*.crate` files (Java only reads top-level with `listFiles()`)
- Sort alphabetically with `sorted()`
- Write `neworder.pref` with UTF-16BE encoding: `open(pref_file, 'w', encoding='utf-16-be')`
- Format: `[begin record]\n` then one `[crate]<name>\n` per crate then `[end record]\n`
- Delete existing `neworder.pref` first if it exists (`Path.unlink()`)
- Use logger `"cdd_sync"`

---

## Verify

```bash
cd /Users/culprit/Git/cdd-sync-pro && python3 -c "
import sys; sys.path.insert(0, 'python')
from sync.pref_sorter import sort_crates
import tempfile
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

## Audit Update (before reporting)

1. Open `md/actions/py-pipeline/AUDIT.md` → Phase 5 row → `✅ DONE`
2. Fill `Verify Output` and `Pass/Fail`

---

## Report

```
Phase: 5
File: python/sync/pref_sorter.py
Verify output: [raw output]
Status: PASS / DEVIATION
Deviation: [description, or NONE]
```

**You are done. Stop here. Do not read the next phase. Do not continue.**
