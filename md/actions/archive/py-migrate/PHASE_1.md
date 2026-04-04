# Phase 1 Executor Prompt — cdd-sync-pro / py-migrate

> **Plan:** `md/actions/py-migrate/PLAN.md`
> **Phase:** 1 — Project Scaffold & Dependencies
> **File(s):** `python/pyproject.toml`, `python/requirements.txt`, `python/requirements-dev.txt`, `python/.gitignore`
> **Verify you have the correct plan file before reading further. If the path does not match your task, STOP AND REPORT.**

## Your Only Job

Execute **Phase 1 only** of `md/actions/py-migrate/PLAN.md`.
One concern. One action. Stop immediately after the Verify block passes.
**Do NOT read other phases. Do NOT continue to Phase 2.**

---

## Mandatory Pre-Flight (Phase 1)

Run all checks before touching a single file. Every check must pass or STOP AND REPORT.

| Check | Expected | STOP condition |
|-------|----------|----------------|
| `pwd` | `/Users/culprit/Git/cdd-sync-pro` | Wrong directory → STOP |
| `git status` | clean working tree | Uncommitted changes → STOP |
| `git branch` | `python` | Wrong branch → STOP |
| `python/` dir exists | `python/` present and empty | Dir missing → STOP |
| Plan file readable | `md/actions/py-migrate/PLAN.md` present | Plan missing → STOP |

---

## Execution

Create four files exactly as specified below.

**File 1:** `python/pyproject.toml`

```toml
[build-system]
requires = ["setuptools>=68"]
build-backend = "setuptools.backends.legacy:build"

[project]
name = "cdd-sync-pro"
version = "2.0.0"
description = "Serato DJ crate synchronization tool"
requires-python = ">=3.12"
dependencies = [
    "flet>=0.21",
    "pyyaml>=6.0",
]

[project.optional-dependencies]
dev = [
    "pytest>=8.0",
    "pytest-asyncio>=0.23",
]

[tool.pytest.ini_options]
testpaths = ["tests"]
asyncio_mode = "auto"
```

**File 2:** `python/requirements.txt`

```
flet>=0.21
pyyaml>=6.0
```

**File 3:** `python/requirements-dev.txt`

```
-r requirements.txt
pytest>=8.0
pytest-asyncio>=0.23
```

**File 4:** `python/.gitignore`

```
__pycache__/
*.py[cod]
*.egg-info/
dist/
build/
.venv/
venv/
.pytest_cache/
*.log
config.yaml
```

---

## Verify

```bash
cd /Users/culprit/Git/cdd-sync-pro/python && python3 --version && cat pyproject.toml | grep 'name = '
```

Expected output contains: `Python 3.12` and `name = "cdd-sync-pro"`

- **Match → proceed to Report.**
- **No match → STOP AND REPORT. Do not attempt to fix it yourself.**

---

## Audit Update (Mandatory — before reporting)

1. Open `md/actions/py-migrate/AUDIT.md`
2. Find the row for Phase 1
3. Set `Status` → `✅ DONE` (or `⚠️ DEVIATION` if verify did not match)
4. Fill `Verify Output` with the actual command output (truncate if >5 lines)
5. Set `Pass/Fail` → ✅ PASS or ⚠️ DEVIATION

---

## Report

Return exactly the following. Nothing else.

```
Phase: 1
File: python/pyproject.toml, python/requirements.txt, python/requirements-dev.txt, python/.gitignore
Verify output: [raw output]
Status: PASS / DEVIATION
Deviation: [exact description, or NONE]
```

**You are done. Stop here. Do not read the next phase. Do not continue.**
