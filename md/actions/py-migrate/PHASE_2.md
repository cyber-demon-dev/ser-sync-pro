# Phase 2 Executor Prompt — cdd-sync-pro / py-migrate

> **Plan:** `md/actions/py-migrate/PLAN.md`
> **Phase:** 2 — Core: path_utils, binary_utils, serato_parser
> **File(s):** `python/core/__init__.py`, `python/core/path_utils.py`, `python/core/binary_utils.py`, `python/core/serato_parser.py`
> **Verify you have the correct plan file before reading further. If the path does not match your task, STOP AND REPORT.**

## Your Only Job

Execute **Phase 2 only** of `md/actions/py-migrate/PLAN.md`.
One concern. One action. Stop immediately after the Verify block passes.
**Do NOT read other phases. Do NOT continue to Phase 3.**

---

## Mandatory Pre-Flight (Phase 2)

| Check | Expected | STOP condition |
|-------|----------|----------------|
| `pwd` | `/Users/culprit/Git/cdd-sync-pro` | Wrong directory → STOP |
| `git status` | clean working tree | Uncommitted changes → STOP |
| `git branch` | `python` | Wrong branch → STOP |
| `python/pyproject.toml` exists | File present | Missing → STOP (Phase 1 not done) |
| Plan file readable | `md/actions/py-migrate/PLAN.md` present | Plan missing → STOP |

---

## Execution

Create the `python/core/` package with the following four files. Read Phase 2 in `md/actions/py-migrate/PLAN.md` for the complete verbatim file contents. Write each file exactly as specified — no additions, no removals.

**File 1:** `python/core/__init__.py` — empty file (package marker)

**File 2:** `python/core/path_utils.py` — copy verbatim from PLAN.md Phase 2, File 2

**File 3:** `python/core/binary_utils.py` — copy verbatim from PLAN.md Phase 2, File 3

**File 4:** `python/core/serato_parser.py` — copy verbatim from PLAN.md Phase 2, File 4

---

## Verify

```bash
cd /Users/culprit/Git/cdd-sync-pro && python3 -c "
import sys; sys.path.insert(0, 'python')
from core.path_utils import normalize_path, normalize_path_for_database, normalize_for_dedup
import unicodedata
nfd = unicodedata.normalize('NFD', 'Niña')
assert normalize_path(nfd) == 'niña', 'NFC normalization failed'
assert normalize_path_for_database('/Volumes/MyDrive/Crates/foo.mp3') == 'Crates/foo.mp3', 'Volume strip failed'
assert normalize_for_dedup('/Volumes/Drive/Crates/Foo.mp3') == 'foo.mp3', 'Dedup key failed'
print('Phase 2 core utils: ALL PASS')
"
```

Expected: `Phase 2 core utils: ALL PASS`

- **Match → proceed to Report.**
- **No match → STOP AND REPORT. Do not attempt to fix it yourself.**

---

## Audit Update (Mandatory — before reporting)

1. Open `md/actions/py-migrate/AUDIT.md`
2. Find the row for Phase 2
3. Set `Status` → `✅ DONE` (or `⚠️ DEVIATION`)
4. Fill `Verify Output` with actual output (truncate if >5 lines)
5. Set `Pass/Fail` → ✅ PASS or ⚠️ DEVIATION

---

## Report

Return exactly the following. Nothing else.

```
Phase: 2
File: python/core/__init__.py, python/core/path_utils.py, python/core/binary_utils.py, python/core/serato_parser.py
Verify output: [raw output]
Status: PASS / DEVIATION
Deviation: [exact description, or NONE]
```

**You are done. Stop here. Do not read the next phase. Do not continue.**
