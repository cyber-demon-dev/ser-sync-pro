# Phase 3 Executor Prompt — cdd-sync-pro / py-migrate

> **Plan:** `md/actions/py-migrate/PLAN.md`
> **Phase:** 3 — Binary Round-Trip Tests
> **File(s):** `python/tests/__init__.py`, `python/tests/test_path_utils.py`, `python/tests/test_serato_parser.py`
> **Verify you have the correct plan file before reading further. If the path does not match your task, STOP AND REPORT.**

## Your Only Job

Execute **Phase 3 only** of `md/actions/py-migrate/PLAN.md`.
One concern. One action. Stop immediately after the Verify block passes.
**Do NOT read other phases. Do NOT continue to Phase 4.**

---

## Mandatory Pre-Flight (Phase 3)

| Check | Expected | STOP condition |
|-------|----------|----------------|
| `pwd` | `/Users/culprit/Git/cdd-sync-pro` | Wrong directory → STOP |
| `git status` | clean working tree | Uncommitted changes → STOP |
| `git branch` | `python` | Wrong branch → STOP |
| `python/core/serato_parser.py` exists | File present | Missing → STOP (Phase 2 not done) |
| Plan file readable | `md/actions/py-migrate/PLAN.md` present | Plan missing → STOP |

---

## Execution

Install dev dependencies, then create the test package. Read Phase 3 in `md/actions/py-migrate/PLAN.md` for complete verbatim file contents.

**Step 1 — Install dependencies:**
```bash
cd /Users/culprit/Git/cdd-sync-pro/python
python3 -m pip install -r requirements-dev.txt
```

**File 1:** `python/tests/__init__.py` — empty file (package marker)

**File 2:** `python/tests/test_path_utils.py` — copy verbatim from PLAN.md Phase 3, File 2

**File 3:** `python/tests/test_serato_parser.py` — copy verbatim from PLAN.md Phase 3, File 3

---

## Verify

```bash
cd /Users/culprit/Git/cdd-sync-pro/python && python3 -m pytest tests/ -v 2>&1 | tail -20
```

Expected: All test lines show `PASSED`. Zero `FAILED`, zero `ERROR`.

- **Match → proceed to Report.**
- **No match → STOP AND REPORT. Do not attempt to fix it yourself.**

---

## Audit Update (Mandatory — before reporting)

1. Open `md/actions/py-migrate/AUDIT.md`
2. Find the row for Phase 3
3. Set `Status` → `✅ DONE` (or `⚠️ DEVIATION`)
4. Fill `Verify Output` with actual pytest summary line (e.g. `14 passed in 0.12s`)
5. Set `Pass/Fail` → ✅ PASS or ⚠️ DEVIATION

---

## Report

Return exactly the following. Nothing else.

```
Phase: 3
File: python/tests/__init__.py, python/tests/test_path_utils.py, python/tests/test_serato_parser.py
Verify output: [raw output — pytest summary line]
Status: PASS / DEVIATION
Deviation: [exact description, or NONE]
```

**You are done. Stop here. Do not read the next phase. Do not continue.**
