# Changelog — cdd-sync-pro

All notable changes to this project will be documented in this file.

---

## [Unreleased]

- **4-step sync pipeline**: `runSync()` now executes four discrete, non-overlapping steps instead of the old monolithic `cdd_sync_library.writeTo()` approach. Step 1 fixes broken paths in database V2; Step 2 repairs broken paths in existing hand-curated crates (via `setTracksRaw()` — no dedup, no removal); Step 3 appends new tracks to existing folder-mapped crates; Step 4 creates new crates only for directories with no matching crate file on disk. Existing crates are never overwritten.
- **Fix Paths standalone mode**: New amber **Fix Paths** button in the GUI runs `runFixPaths()` — scans and repairs broken paths in existing crates and database V2 without writing any new crates.
- **Per-step diagnostic log files**: Each sync session now creates seven timestamped log files: main log, dupe log, path-fix log, and four step-level logs (`step1-db-fix`, `step2-crate-fix`, `step3-append`, `step4-create`). Verbose per-path detail never floods the GUI; it goes to file only.
- **GUI tooltip documentation**: All sync options and duplicate management controls now show detailed `[DEBUG]`-tagged tooltips explaining exact behavior, config key, and interaction effects.
- **Clear-library guard**: Enabling **Clear library before sync** now requires confirmation via a warning dialog before the checkbox can be checked.

- **CI pipeline**: GitHub Actions workflow (`.github/workflows/build.yml`) now runs `ant test` on every push and pull request targeting `master`. Build status badge added to `README.md`.
- **`--dry-run` CLI flag**: Pass `--dry-run` as a command-line argument (CLI mode only) to preview a full sync without writing anything to disk. All 7 write sites (backup, dupe mover, database fixer, Serato folder creation, parent crate creation, crate library write, broken path fixer, crate sorter) log `[DRY RUN] Would have: ...` instead of executing. Exits with `[DRY RUN] Sync complete — no files were written.`. GUI mode is unaffected.
- **Codebase cleanup**: Consolidated 4 duplicated path normalization methods into `ser_sync_binary_utils.normalizePath()` and `normalizePathForDatabase()`. Fixed stream leaks in config constructors (try-with-resources). Converted `ser_sync_dupe_mover` from static mutable state to instance-based (fixes re-entrant GUI bug). Replaced 17 regex `Pattern` objects in `ser_sync_media_library.isMedia()` with a `Set<String>` lookup. Indexed otrk blocks in `ser_sync_database_fixer` to eliminate O(N²) scanning. Removed verbose per-group dupe logging from GUI output. Removed unnecessary `System.exit(0)` from session-fixer. Fixed test compilation target (1.8 → 11). Added path normalization and crate round-trip tests (26 total).
- **Silo restructure**: Moved source files into `shared/src/` (9 shared), `cdd-sync-pro/src/` (11 app-only), and `session-fixer/src/` (4, unchanged). Updated `build.xml` to compile from all three directories.
- **Bug fix**: Smart crate write always rewrote all 138 crates due to `Collections.unmodifiableCollection()` not overriding `equals()`. Changed `getColumns()` to return `unmodifiableList()` for proper element comparison.
- **Logs to volume**: Log files now write to `<volume>/cdd-sync-pro/logs/` alongside backup and dupes instead of CWD-relative `logs/`.
- **GUI config window**: New interactive dark-themed config panel (`ser_sync_pro_window.java`) with path fields + Browse buttons, sync option checkboxes, duplicate management dropdowns, log output area, and Start/Cancel buttons. Runs sync on SwingWorker background thread. Settings persist to `ser-sync.properties`. Session-fixer unaffected (Option A architecture).

## Past Highlights (from Git history)

- **dc8b11a** — Create `dependabot.yml` for automated dependency updates
- **0a8ee2f** — Add detailed guide for the Serato `.session` file format
- **cc94a8a** — Introduce batch synchronization mode with config file support and server-side rename detection
- **b69bfe7** — Allow date-based selection of Serato database entries when fixing duplicate broken paths in crates
- **982ac64** — Refactor: Move session-fixer to standalone silo
