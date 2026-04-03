# Flet GUI Window — Plan

> Feature: `py-gui`
> Scope: Build a polished dark-mode Flet GUI window equivalent to the Java `cdd_sync_pro_window.java` — config panel, live log output, Start/Cancel, integrated with the existing `run_sync()` pipeline.
> Execute with: `/run-phase py-gui`

## Context

| Item | Value |
|------|-------|
| Project root | `/Users/culprit/Git/cdd-sync-pro` |
| Branch | `python` |
| Runtime | `Python 3.12+` — venv at `python/.venv` |
| Test command | `cd python && .venv/bin/python gui.py` (visual verify) |
| Key files | `python/gui.py` [NEW], `python/main.py` [MODIFY], `python/config.py`, `python/sync/pipeline.py` |
| Current state | CLI pipeline fully functional (`main.py --dry-run`). No GUI exists yet. Flet ≥ 0.21 declared in `requirements.txt` but not installed in venv. |

---

## Phase 1 — Flet Install + App Shell

**File(s):** `python/gui.py` [NEW]
**Reference:** `java/cdd-sync-pro/src/cdd_sync_pro_window.java` (window dimensions, panel structure)

**Intent:** Install Flet into the venv. Create `python/gui.py` with a minimal `ft.app(target=main)` that opens a 780×760 window titled `"cdd-sync-pro"`, dark theme (`ft.ThemeMode.DARK`), and SF Pro font (`page.fonts`, `page.theme.font_family`). Window should display a single placeholder `ft.Text("cdd-sync-pro")` centered. No controls yet — just confirm the stack works.

**Constraints:**
- Install via `python/.venv/bin/pip install flet` — do NOT modify `requirements.txt` yet (Phase 4 does that).
- Use `ft.ThemeMode.DARK` — do NOT manually set background colors in this phase.
- Font: `page.theme = ft.Theme(font_family="SF Pro Display")` — macOS resolves this from system fonts automatically.
- Window must not be resizable for now (`page.window.resizable = False`).

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro/python && .venv/bin/python gui.py
```
Window opens, dark background, "cdd-sync-pro" text visible. Close it — exit 0.

⛔ STOP — Report result. Wait for user.

---

## Phase 2 — Config Panel

**File(s):** `python/gui.py` [MODIFY]
**Reference:** `java/cdd-sync-pro/src/cdd_sync_pro_window.java` lines 90–225 (config panel layout, all control names and defaults)

**Intent:** Replace the placeholder with a full top config panel inside a `ft.Column`. Three sections:

**Paths row** — Three `ft.TextField` + `ft.ElevatedButton("Browse")` rows for: Music Folder, Serato Path, Parent Crate. Browse opens `ft.FilePicker` in directory-only mode. Parent Crate has no Browse (text only, optional).

**Pipeline Steps** — `ft.Container` with title "Pipeline Steps". Two-column grid (`ft.Row` pairs) of `ft.Checkbox` controls in execution order:
- Backup (default: on), Clear Library ⚠️ (default: off)
- Fix Database Paths / Fix Crate Paths (both default: on)
- Append Existing Crates / Create New Crates (both default: on)
- Reset Crates A→Z (default: off, spans full row)

**Duplicate Management** — `ft.Container` with title "Duplicate Management". Scan checkbox (default: off) + two `ft.Dropdown` rows: Detection (`name-and-size` / `name-only` / `off`) and Move mode (`keep-oldest` / `keep-newest` / `false`).

**Load from config:** If `config.yaml` exists alongside `gui.py`, call `SyncConfig.load()` and populate all controls from it on startup. Use try/except — missing config is not an error.

**Constraints:**
- No Start/Cancel buttons yet — placeholder space at bottom is fine.
- All control refs stored as instance state (not local vars) — Phase 3 needs them.
- `ft.FilePicker` must be added to `page.overlay` before use.
- Clear Library checkbox: show `ft.AlertDialog` warning if user checks it (mirrors Java confirm dialog).

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro/python && .venv/bin/python gui.py
```
All three path rows visible with Browse buttons. Pipeline Steps and Dupe Management panels render. Browse button opens a folder picker dialog.

⛔ STOP — Report result. Wait for user.

---

## Phase 3 — Log Panel + Start/Cancel Wiring

**File(s):** `python/gui.py` [MODIFY]
**Reference:** `java/cdd-sync-pro/src/cdd_sync_pro_window.java` lines 227–300, 471–537; `python/sync/pipeline.py` (`run_sync` signature + log_callback)

**Intent:** Complete the window with a live log output area and functional Start/Cancel buttons.

**Log panel** — `ft.ListView` (expand=True, auto_scroll=True) inside a labeled `ft.Container` with title "Log Output". Monospace font (`"Courier New"` or `"Menlo"`). Each log line appended as a `ft.Text` control; call `page.update()` after each append to push to UI. This is the `log_callback` passed to `run_sync()`.

**Start button** — Green accent. On click:
1. Validate Music Folder and Serato Path are non-empty — show `ft.SnackBar` if missing.
2. Build `SyncConfig` from current control values.
3. Save config to `config.yaml` (call `cfg.save(Path("config.yaml"))`).
4. Disable all controls + Start; enable Cancel.
5. Launch `run_sync(cfg, log_callback=_append_log)` on a `threading.Thread(daemon=True)`.
6. On thread completion (use `threading.Event` or thread join in a `page.run_thread`): re-enable controls, disable Cancel, append "✅ Sync Complete" or "❌ Sync failed".

**Cancel button** — Red accent when active, grey when inactive. Sets a `threading.Event` that `run_sync()` doesn't check natively — for now, Cancel just disables itself and appends "[CANCELLED] Stop requested — sync will finish current step." (full cooperative cancel is a future feature).

**Constraints:**
- ALL `page.update()` calls must happen from the UI thread or via `page.run_thread` — never call `page.update()` directly from the background sync thread. Use `page.run_thread(lambda: ...)` to marshal UI updates.
- The log callback: `def _append_log(msg): page.run_thread(lambda: (_log_list.controls.append(ft.Text(msg, ...)), page.update()))`.
- `SyncConfig` construction from GUI state: map each checkbox/dropdown value to the correct `SyncConfig` field — reference `python/config.py` for field names.

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro/python && .venv/bin/python gui.py
```
Click Start with valid (real or fake) paths — log panel shows "cdd-sync-pro started" and subsequent pipeline messages in real time. Cancel button activates during sync. Controls re-enable when sync finishes.

⛔ STOP — Report result. Wait for user.

---

## Phase 4 — Wire main.py + Final Polish

**File(s):** `python/main.py` [MODIFY], `python/requirements.txt` [MODIFY]

**Intent:** Two things:

**1. main.py GUI/CLI split** — When `--cli` flag is passed, run headless as today. Otherwise, launch the Flet GUI. New flow:
```
--cli → existing SyncConfig.load() + run_sync() path
(no flag) → import gui; gui.launch()
```
Extract the Flet `ft.app(target=...)` call into a `launch()` function in `gui.py` that `main.py` can call.

**2. requirements.txt** — Add `flet>=0.21.0` to `python/requirements.txt` (it's in pyproject.toml already — add to requirements.txt for pip install compatibility).

**Polish pass on `gui.py`:**
- Window minimum size: `page.window.min_width = 780`, `page.window.min_height = 760`.
- Remove `page.window.resizable = False` — allow vertical resize for log panel.
- Set `page.window.title_bar_hidden = False` (keep native title bar).
- Set `page.padding = 16`.
- Ensure all `ft.Container` sections have consistent `border_radius=8`, subtle border (`ft.border.all(1, ft.colors.OUTLINE)`).
- Progress label above buttons: `ft.Text("Ready", ref=_status_ref)` — update to "Running…" on Start, "Done" on finish.

**Constraints:**
- `gui.py` must not import anything at module level that fails when Flet is absent — guard `import flet as ft` inside `launch()` or at top with a clear error.
- `main.py --cli` path must still work without Flet installed (don't import `gui` at the top of `main.py`).

**Verify:**
```bash
# GUI mode (default)
cd /Users/culprit/Git/cdd-sync-pro/python && .venv/bin/python main.py

# CLI mode
cd /Users/culprit/Git/cdd-sync-pro/python && .venv/bin/python main.py --cli --dry-run
```
GUI opens with no args. CLI runs headless with `--cli`. Both exit cleanly.

⛔ STOP — Report result. Wait for user.

---

## Phase 5 — Commit

**Action:** Stage and commit all GUI files.

```bash
git add python/gui.py python/main.py python/requirements.txt md/AGENT_LOG.md
git commit -m "feat(python): flet dark-mode GUI window — config panel, live log, start/cancel"
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

- [ ] `python main.py` opens the Flet dark-mode GUI window
- [ ] All config fields load from `config.yaml` on startup
- [ ] Start button runs `run_sync()` and streams log lines live
- [ ] `python main.py --cli --dry-run` still works headlessly
- [ ] Commit pushed to `origin/python`
