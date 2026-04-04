# Python Audit Bug Fixes — Plan

> Feature: `py-bugfix`
> Scope: Fix 16 findings from the dead-code & bug audit across python/
> Execute with: `/run-phase py-bugfix`

## Context

| Item | Value |
|------|-------|
| Project root | `/Users/culprit/Git/cdd-sync-pro` |
| Branch | `python` |
| Runtime | `Python 3.x (.venv)` |
| Test command | `cd python && .venv/bin/python -m pytest tests/ -v` |
| Key files | `python/gui.py, python/sync/pipeline.py, python/config.py, python/core/serato_parser.py, python/sync/media_library.py, python/sync/database_fixer.py, python/sync/pref_sorter.py, python/core/path_utils.py` |
| Current state | 16-finding audit complete; all prior plans ✅ DONE; no fixes applied yet |

---

## Phase 1 — Fix `exc` late-binding bug in gui.py (critical)

**File(s):** `python/gui.py`

**Intent:** Every `except Exception as exc:` block inside a `_worker` thread function defines an inner `_err()` closure that references `exc`. Python deletes `exc` from the local namespace at the end of the `except` block (PEP 3110), so by the time `page.run_thread(_err)` fires, `exc` is gone → `NameError` instead of the real error message.

Fix all four affected worker closures by capturing `exc` before the scope ends:

```
# Pattern: capture exc before closure captures it lazily
_exc = exc
def _err(_e=_exc):
    _append_log(f"❌ ... {_e}")
page.run_thread(_err)
```

The four locations are:
1. `_run_backup_alone` `_worker` except block (~line 714)
2. `_run_sort_alone` `_worker` except block (~line 748)
3. `_run_step_alone` `_worker` except block (~line 807)
4. `_run_scan_alone` `_worker` except block (~line 870)

**Constraints:**
- Only touch the except blocks — do not change the try bodies or `_done` closures
- Use `_e=_exc` default-arg pattern consistently across all four

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro/python && grep -n "_exc = exc" gui.py | wc -l
```
Expected: `4` (one per worker)

⛔ STOP — Report result. Wait for user.

---

## Phase 2 — Fix pipeline.py: type annotation, incomplete clear, missing guards

**File(s):** `python/sync/pipeline.py`

**Intent:** Three separate fixes:

1. **`_dry_run_step4` type annotation bug (line ~746):**
   `would_create: List[str]` is declared but tuples `(str, int)` are appended. Change to `List[tuple]` (or `list`).

2. **`_delete_dir_contents` incomplete clear (lines ~847–856):**
   Currently only deletes top-level files; subdirectories and their `.crate` files survive a "clear library" operation. Add recursive deletion of subdirectories:
   ```
   elif entry.is_dir():
       shutil.rmtree(entry)
   ```
   Add `import shutil` at the top of the file if not already present.

3. **`run_step3` / `run_step4` missing serato_path guard:**
   `run_step1` and `run_step2` both have:
   ```
   if not Path(serato_path).is_dir():
       _log(f"Step N: Serato library path does not exist: {serato_path}")
       return
   ```
   Add the same guard at the top of both `run_step3` and `run_step4` (right after setting `serato_path`).

**Constraints:**
- `shutil` is in stdlib — no new dependency
- Do not alter logic outside these three sites

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro/python && .venv/bin/python -m pytest tests/test_pipeline.py -v
```
All existing tests must pass.

⛔ STOP — Report result. Wait for user.

---

## Phase 3 — Fix config.py: alias inversion + unused import

**File(s):** `python/config.py`

**Intent:** Two fixes:

1. **`"newest"` alias maps to wrong constant (line ~93):**
   ```python
   elif raw_move == "newest":
       raw_move = DUPE_MOVE_KEEP_OLDEST   # ← BUG: should be KEEP_NEWEST
   ```
   A user with `dupe_move_mode: newest` wants to keep newest files, but this currently sets `keep-oldest`. Fix: Change `DUPE_MOVE_KEEP_OLDEST` → `DUPE_MOVE_KEEP_NEWEST` on that line.

2. **Unused import (line 11):**
   `from dataclasses import dataclass, field, asdict` — `asdict` is imported but never called. Remove it.

**Constraints:**
- Only touch line 11 and line ~93
- Do not change any other alias logic or field

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro/python && python -c "
from config import SyncConfig, DUPE_MOVE_KEEP_NEWEST, DUPE_MOVE_KEEP_OLDEST
import yaml, io
# Simulate loading 'newest' alias
data = {'music_library_path': '/tmp/a', 'serato_library_path': '/tmp/b', 'dupe_move_mode': 'newest'}
raw = str(data.get('dupe_move_mode', 'false')).strip().lower()
if raw == 'newest':
    raw = DUPE_MOVE_KEEP_NEWEST
assert raw == 'keep-newest', f'Expected keep-newest got {raw}'
print('PASS: newest alias maps to keep-newest')
"
```

⛔ STOP — Report result. Wait for user.

---

## Phase 4 — Remove dead code (3 methods, 3 files)

**File(s):**
- `python/sync/database_fixer.py`
- `python/core/serato_parser.py`
- `python/sync/media_library.py`

**Intent:** Delete three orphaned methods that have zero callers:

1. **`database_fixer.py`** — Delete `_index_otrk_blocks()` function and its comment header block (~lines 209–226). Its own docstring says "Retained for backward compatibility. Not used internally." No external callers found.

2. **`serato_parser.py`** — Delete `Crate.get_uniform_track_name()` method (~lines 83–84). Zero callers in any Python file.

3. **`media_library.py`** — Delete `MediaLibrary.remove_tracks()` method (~lines 59–67). Zero callers — the pipeline re-scans from disk after dupe moves instead of using this.

**Constraints:**
- Delete **only** these three methods and their docstrings — do not touch anything else in the files
- Verify tests still pass (they don't call these methods)

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro/python && .venv/bin/python -m pytest tests/ -v
```
All tests must pass.

⛔ STOP — Report result. Wait for user.

---

## Phase 5 — Atomic pref write + boot-drive path risk

**File(s):**
- `python/sync/pref_sorter.py`
- `python/core/path_utils.py`

**Intent:** Two fixes:

1. **`pref_sorter.py` — atomic write:**
   Currently `neworder.pref` is deleted first, then recreated. If the process dies between those two operations, Serato loses its sort order permanently.
   
   Replace the delete + open pattern with an atomic `.tmp` → `os.replace()` write:
   - Write to `pref_file.with_suffix(".tmp")` first
   - Then `os.replace(tmp_path, pref_file)` — single syscall, no window
   - Remove the `pref_file.unlink()` call
   - Add `import os` if not already present (check — it may already be imported)

2. **`path_utils.py` — `_ROOT_PREFIX_RE` boot-drive guard:**
   `normalize_path_for_database()` strips the first path component from any path that starts with `/` after the volumes regex doesn't match. On a boot-drive path like `/Users/foo/Music/...`, this strips `/Users/` leaving `foo/Music/...` — wrong.
   
   The `_ROOT_PREFIX_RE` fallback was designed for macOS pre-Volumes paths (e.g. `/DriveName/Music`). Restrict it: only apply `_ROOT_PREFIX_RE` when the first component looks like a drive root (not `Users`, `home`, or other well-known system dirs):
   ```
   _SYSTEM_DIRS = frozenset({"users", "home", "var", "usr", "etc", "opt", "private"})
   # Only strip first component if it doesn't match a known system dir
   ```
   Alternatively (safer/simpler): only strip the first component if the path does NOT start with `/Users`, `/home`, `/var`, `/usr`, `/etc`, `/opt`, `/private`. A simple prefix check is cleaner than a full regex.

**Constraints:**
- The `pref_sorter` fix must not change any log messages
- The `path_utils` fix must not break the 3 existing `test_path_utils.py` test cases
- Add `import os` to `pref_sorter.py` only if it isn't already there

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro/python && .venv/bin/python -m pytest tests/test_path_utils.py tests/test_pipeline.py -v
```
All tests must pass.

⛔ STOP — Report result. Wait for user.

---

## Phase 6 — Commit

**Action:** Stage and commit all changes from this plan.

```bash
cd /Users/culprit/Git/cdd-sync-pro
git add python/gui.py python/sync/pipeline.py python/config.py \
        python/core/serato_parser.py python/sync/media_library.py \
        python/sync/database_fixer.py python/sync/pref_sorter.py \
        python/core/path_utils.py
git commit -m "fix(python): audit bug fixes — exc capture, dead code, clear-lib, config alias, atomic pref"
git push origin python
```

**Verify:**
```bash
git log --oneline -1
```

⛔ STOP — Report result. Wait for user.

---

## Done

All phases complete when:

- [ ] GUI error paths display real exception messages (not `NameError`)
- [ ] `clear_library_before_sync` deletes nested crate subdirectories
- [ ] `dupe_move_mode: newest` correctly maps to `keep-newest`
- [ ] 3 dead methods deleted, all tests still pass
- [ ] `neworder.pref` write is atomic (no kill-window)
- [ ] `path_utils` does not mangle boot-drive paths
