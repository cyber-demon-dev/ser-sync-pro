# Session Fixer — Plan

> Feature: `session-fixer`
> Scope: Port `java/session-fixer/src/` to Python and wire into the Flet GUI under a new "Session Fixer" card in the Duplicate Management section.
> Execute with: `/run-phase session-fixer`

## Context

| Item | Value |
|------|-------|
| Project root | `/Users/culprit/Git/cdd-sync-pro` |
| Branch | `python` |
| Runtime | `Python 3.x + Flet` |
| Test command | `cd python && .venv/bin/python -c "from sync.session_fixer import fix_broken_paths; print('OK')"` |
| Key files | `python/sync/session_fixer.py` (new), `python/gui.py` (modify) |
| Current state | Java session-fixer exists as source + JAR in `java/session-fixer/`; no Python equivalent; GUI has a stub "Duplicate Management" section with detection/move dropdowns |

---

## Phase 1 — Python Core: session_fixer.py

**File:** `python/sync/session_fixer.py` [NEW]
**Reference:** `java/session-fixer/src/session_fixer_parser.java`, `java/session-fixer/src/session_fixer_core_logic.java`

**Intent:** Create a pure-Python module that replicates the two Java classes. It must expose two public functions:

1. **`scan_broken_paths(serato_path, music_library_paths, dry_run=True, log_callback=None) → dict`**
   - Reads all `.session` files from `<serato_path>/History/Sessions/`
   - For each `oent` block, extracts field `0x02` (filepath) decoded as UTF-16BE
   - Builds a filename→absolute-path lookup map from every music library path (recursive walk, NFC-normalized keys); first library path wins on collision (process in reverse order)
   - Checks each unique path: if the file is missing, look it up by filename in the library map
   - Returns `{"fixable": {old: new, ...}, "unfixable": [path, ...]}`
   - Logs findings; if `dry_run=True`, does NOT write any files

2. **`fix_broken_paths(serato_path, music_library_paths, dry_run=False, log_callback=None) → tuple[int, int]`**
   - Calls `scan_broken_paths` with `dry_run=False` internally
   - For each fixable mapping, rewrites the `.session` file in-place using atomic write (write to `<file>.tmp` then rename)
   - Handles length field recalculation for `oent`→`adat` blocks exactly as the Java parser does
   - Returns `(sessions_fixed, entries_fixed)`

**Constraints:**
- Session binary format: `vrsn\x00\x00\x00<len><UTF-16BE>` header, then `oent\x00\x00\x00<len>` entries each containing an `adat` sub-block with field-ID / 4-byte-length / value triplets (all big-endian)
- Field `0x02` = filepath (UTF-16BE, may contain trailing `\x00` chars — preserve them on write)
- Trailing null count must be preserved on path replacement (same as Java `rebuildEntry`)
- Use `unicodedata.normalize("NFC", filename)` for lookup keys
- No external deps; stdlib only (`pathlib`, `struct`, `os`, `unicodedata`, `concurrent.futures`)
- `log_callback` signature: `(str) -> None` — same as rest of pipeline

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro/python && .venv/bin/python -c "
from sync.session_fixer import scan_broken_paths, fix_broken_paths
print('import OK')
"
```

⛔ STOP — Report result. Wait for user.

---

## Phase 2 — GUI: Session Fixer Section in Duplicate Manager

**File:** `python/gui.py` [MODIFY]
**Reference:** `python/sync/session_fixer.py` (Phase 1 output)

**Intent:** Add a "Session Fixer" sub-section to the existing "Duplicate Management" `_section()` block. The section must contain:

1. **Checkbox** — `cb_session_fix` — label: `"Fix broken session paths"`, default `False`. Uses `_checkbox()` helper.
2. **Scan button** (green-teal style matching existing `🔍 Scan` buttons) — calls `_run_session_scan()` on click
3. **Run button** (blue style matching existing `▶ Run` buttons) — calls `_run_session_fix()` on click
4. A helper `ft.Text` hint: `"Repairs broken file paths in Serato History/Sessions/*.session files"`

Layout: same card-pill pattern as pipeline step rows (refer to `_card_step_row` for styling). Create a new helper `_card_session_fixer_row(cb, scan_ref, run_ref)` that mirrors `_card_step_row` — no `step_n` needed.

Wire two worker functions (daemon threads):
- **`_run_session_scan()`**: validates `serato_path` + `music_path`, calls `scan_broken_paths(serato_path, [music_path], dry_run=True, log_callback=_append_log)`, logs summary to the log panel, re-enables controls
- **`_run_session_fix()`**: same validation, calls `fix_broken_paths(...)` with `dry_run=False` only if `cb_session_fix.value` is True; re-enables controls; shows snackbar warning if checkbox is unchecked

Add two new Refs: `_sf_scan_ref`, `_sf_run_ref` (type `ft.Ref[ft.FilledButton]`).

Include both refs in `_set_controls_enabled()` alongside the existing run/scan refs.

**Constraints:**
- `music_library_paths` is a list; pass `[music_field.value.strip()]` — single path for now; multi-path is a future concern
- No new configuration keys needed — Serato path and Music Folder come from existing fields
- Keep `cb_session_fix` state separate from `cb_dupe_scan` — they are independent features
- The new card sits **below** the existing dupe dropdowns inside the same `_section("Duplicate Management", ...)` block

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro/python && .venv/bin/python -c "
import ast
with open('gui.py') as f:
    src = f.read()
ast.parse(src)
print('syntax OK')
print('session scan ref:', '_sf_scan_ref' in src)
print('session run ref:', '_sf_run_ref' in src)
"
```

⛔ STOP — Report result. Wait for user.

---

## Phase 3 — Smoke Test (Live GUI)

**File:** N/A — runtime validation only

**Intent:** Launch the GUI and confirm the new Session Fixer card renders correctly without crashing. No actual Serato session files needed for this phase.

**Constraints:**
- Kill any running `main.py` process first (one Flet socket at a time)
- Use the existing `.venv`

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro/python && pkill -f "main.py" 2>/dev/null; sleep 1
.venv/bin/python main.py &
sleep 3
echo "GUI launched — inspect window for Session Fixer card under Duplicate Management"
```

Observable check: GUI window opens, "Duplicate Management" section shows `cb_session_fix` checkbox + Scan + Run buttons below the existing dupe detection row. No import errors in terminal.

⛔ STOP — Report result. Wait for user.

---

## Phase 4 — Commit

**Action:** Stage and commit all changes from this plan.

```bash
cd /Users/culprit/Git/cdd-sync-pro
git add python/sync/session_fixer.py python/gui.py
git commit -m "feat(session-fixer): port Java session path fixer to Python + GUI scan/run card"
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

- [ ] `python/sync/session_fixer.py` imports cleanly and exports `scan_broken_paths` + `fix_broken_paths`
- [ ] GUI renders "Session Fixer" card with checkbox, Scan, and Run buttons under Duplicate Management
- [ ] GUI launches without errors
- [ ] Commit pushed to `origin/python`
