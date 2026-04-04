# GUI Step Buttons — Plan

> Feature: `gui-steps`
> Scope: Replace Pipeline Steps checkboxes with hybrid toggle+run-button rows so each step can be run independently in addition to the full-pipeline Start.
> Execute with: `/run-phase gui-steps`

## Context

| Item | Value |
|------|-------|
| Project root | `/Users/culprit/Git/cdd-sync-pro` |
| Branch | `python` |
| Runtime | `Python 3.12+` — venv at `python/.venv` |
| Test command | `cd python && .venv/bin/python main.py` (visual verify) |
| Key files | `python/gui.py`, `python/sync/pipeline.py` |
| Current state | Pipeline Steps section has 7 checkboxes (enable/disable per step) + a single Start button that runs all enabled steps. No way to run a single step in isolation. |

---

## Phase 1 — Pipeline Step Runner Functions

**File(s):** `python/sync/pipeline.py`
**Reference:** Existing `update_database_paths`, `fix_existing_crates`, `append_new_tracks`, `create_new_crates`, `create_backup`, `sort_crates` functions already in this file.

**Intent:** Expose four public `run_step_N(config, log_callback)` wrapper functions that a caller can invoke independently, without running the full `run_sync()` pipeline. Each wrapper must:
1. Run the media library scan (always required as prerequisite)
2. Load the Serato database (required for Steps 2–4)
3. Call the corresponding step helper
4. Respect `config.dry_run`

Create: `run_step1(config, log_callback)`, `run_step2(config, log_callback)`, `run_step3(config, log_callback)`, `run_step4(config, log_callback)`.

**Constraints:**
- Each function is fully self-contained — no shared state with `run_sync()`.
- All log lines must route through `log_callback` AND `logger.info` (same pattern as existing step helpers).
- `run_step1` and `run_step2` skip gracefully if config paths don't exist.
- Do NOT refactor `run_sync()` — leave it intact. These are additive.
- Backup and Reset Crates A→Z are NOT given individual runners (they remain full-pipeline only).

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro/python && \
  .venv/bin/python -c "from sync.pipeline import run_step1, run_step2, run_step3, run_step4; print('OK')"
```
Prints `OK`, no import errors.

⛔ STOP — Report result. Wait for user.

---

## Phase 2 — GUI Step Row Redesign

**File(s):** `python/gui.py`
**Reference:** Current `pipeline_grid` column in `main()` (~line 185). Read the full `main()` function before editing.

**Intent:** Replace the two-per-row checkbox grid with a table-style layout. Each pipeline step (Fix Database Paths, Fix Crate Paths, Append Existing Crates, Create New Crates) gets its own row:

```
[✓ checkbox]  Step label (expand)         [▶ Run]
```

- Checkbox = "include in full Start run" (same as today)
- `▶ Run` button = runs that step alone (calls the Phase 1 runner function on a daemon thread)
- Backup and Reset A→Z keep their existing checkbox-only row (no individual run button needed)
- Dry Run checkbox remains at the bottom, unchanged

Each `▶ Run` button shares the same log panel and status label as the Start button. While a step is running, disable all `▶ Run` buttons and the Start button (use the existing `_set_controls_enabled` pattern or a new `_set_running(True/False)` helper).

**Constraints:**
- Step runner buttons must respect the `cb_dry_run` checkbox (pass `cfg.dry_run` through).
- Do NOT remove the full Start button — it still runs all enabled steps via `run_sync()`.
- `_set_controls_enabled(False)` already disables everything during a run — reuse it.
- Config build for individual runs: construct a `SyncConfig` from current fields (call `_build_config`), then invoke the appropriate `run_stepN`.
- Step label width should be fixed (`expand=True`) so `▶ Run` buttons right-align consistently.

**Verify:**
```bash
cd /Users/culprit/Git/cdd-sync-pro/python && .venv/bin/python main.py
```
GUI opens. Pipeline Steps shows 4 table rows with checkboxes + `▶` buttons, plus Backup and Reset A→Z rows. Clicking `▶` on Step 1 (with Dry Run checked) logs `[DRY RUN] Step 1:` lines in the log panel without running other steps.

⛔ STOP — Report result. Wait for user.

---

## Phase 3 — Commit

**Action:** Stage and commit.

```bash
git add python/gui.py python/sync/pipeline.py
git commit -m "feat(python): per-step run buttons — hybrid checkbox+runner GUI layout"
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

- [ ] `from sync.pipeline import run_step1, run_step2, run_step3, run_step4` imports cleanly
- [ ] GUI opens with 4 step rows each showing a `▶` run button
- [ ] Clicking `▶` on a step runs it in isolation (log panel shows only that step's output)
- [ ] Full `Start` button still runs all enabled steps end-to-end
- [ ] Dry Run checkbox is respected by both full run and individual step runs
