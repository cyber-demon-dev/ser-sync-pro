# Phase 6 Executor — cdd-sync-pro / py-pipeline

> **Plan:** `md/actions/py-pipeline/PLAN.md`
> **Phase:** 6 — Database Fixer (`python/sync/database_fixer.py`)
> **File(s):** `python/sync/database_fixer.py`

## Your Only Job

Execute **Phase 6 only**. Stop after Verify passes.

---

## Mandatory Pre-Flight

| Check | Expected | STOP condition |
|-------|----------|----------------|
| `pwd` | `/Users/culprit/Git/cdd-sync-pro` | Wrong dir → STOP |
| `git status` | clean working tree | Uncommitted changes → STOP |
| `git branch` | `python` | Wrong branch → STOP |
| `python/sync/pref_sorter.py` exists | present | Missing → STOP (Phase 5 not done) |
| `md/actions/py-pipeline/PLAN.md` | present | Missing → STOP |

---

## Execution

**Step 1:** Read `java/shared/src/cdd_sync_database_fixer.java` in full.

**Step 2:** Read Phase 6 in `md/actions/py-pipeline/PLAN.md` in full.

**Step 3:** Create `python/sync/database_fixer.py` implementing `update_paths(database_path, path_fixes)` per the contract. Key requirements:
- Single-pass binary TLV walk of the `database V2` file
- For each `otrk` block: walk inner TLV to find `pfil` value
- If `pfil` value matches a key in `path_fixes`: replace the payload with the new value encoded as UTF-16BE; recalculate `pfil` block length AND `otrk` block length
- Rebuild the output byte buffer with the patched blocks
- Write patched bytes back to the same file path
- Use `core.binary_utils` for `read_big_endian_int`, `decode_utf16be`, `encode_utf16be`
- Use `struct.pack('>I', n)` for 4-byte BE length fields
- Use logger `"cdd_sync"`. On `IOError`: log and `return 0`

---

## Verify

```bash
cd /Users/culprit/Git/cdd-sync-pro && python3 -c "
import sys; sys.path.insert(0, 'python')
from sync.database_fixer import update_paths
from core.serato_parser import SeratoDatabase
import tempfile, io, struct
from pathlib import Path
from core.binary_utils import encode_utf16be

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

with tempfile.NamedTemporaryFile(delete=False, suffix='_db') as f:
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

## Audit Update (before reporting)

1. Open `md/actions/py-pipeline/AUDIT.md` → Phase 6 row → `✅ DONE`
2. Fill `Verify Output` and `Pass/Fail`

---

## Report

```
Phase: 6
File: python/sync/database_fixer.py
Verify output: [raw output]
Status: PASS / DEVIATION
Deviation: [description, or NONE]
```

**You are done. Stop here. Do not read the next phase. Do not continue.**
