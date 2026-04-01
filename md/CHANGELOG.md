# Changelog — cdd-sync-pro

All notable changes to this project will be documented in this file.

---

## [Unreleased]

- **Fix: Serato crate column widths preserved on round-trip rewrite (Step 2)**: Crates rewritten by Step 2 (path fixer) no longer show as blank in Serato. Root cause: `writeTo()` hardcoded `tvcw = "0"` for all columns, destroying the pixel widths Serato stores per-crate. Fix: `readFrom()` now captures the raw `ovct` and `osrt` TLV payloads as byte arrays; `writeTo()` emits them verbatim when present, bypassing reconstruction. New crates (Step 4) are unaffected — raw payloads absent → existing default logic runs unchanged.
  - `cdd_sync_crate.java`: Added `rawOsrtPayload` / `rawOvctPayloads` fields; `writeTo()` branches on their presence.

- **Refactor: `cdd_sync_crate.readFrom()` → unified TLV walker**: Replaced the two-loop, `mark/reset`-peek implementation with a single `while` loop that reads each top-level block into a `byte[] payload` before dispatch. Eliminates stream slippage on variable-length `ovct`/`osrt` blocks (the original cause of certain crates — e.g. `Current%%Base%%2026` — being parsed with 0 tracks). Three private payload-only helpers `extractPtrk`, `extractTvcn`, `extractOsrt` + a shared `walkPayloadForTag` walker replace the bespoke per-tag byte arithmetic.
  - `cdd_sync_crate.java`: `readFrom()` rewritten; four private helpers added; `writeTo()` / public API unchanged.

- **Fix: Step 2 crate write now uses in-place mutation**: `fixExistingCrates()` previously built a scratch `fixedCrate` object and manually copied version/sorting/columns, risking silent field mismatches. Now mutates the already-read `crate` directly via `setTracksRaw()` and calls `writeTo()` on it — identical pattern to Steps 3 and 4.
  - `cdd_sync_crate_fixer.java`: Scratch-copy pattern removed.

- **Per-step pipeline debug toggles**: Each of the five sync pipeline steps (Step 0–4) can now be independently enabled or disabled from the **Pipeline Steps** panel in the GUI. All previous sync option controls (Backup, Clear Library, Sort Crates) have been merged into this single panel, displayed in execution order (Pre-1 → Pre-2 → Step 0 → Step 1 → Step 2 → Step 3 → Step 4 → Post). Toggles are also exposed as `sync.step0.enabled`–`sync.step4.enabled` properties for CLI/config-file use. Allows any single step to be isolated without running the full pipeline.

- **Fix: Step 2 now processes ALL crates including hand-curated Live sets**: Replaced the multi-threaded, ambiguous-lookup Step 2 implementation with a simple sequential loop. All `.crate` files in `Subcrates/` are now processed regardless of whether they map to a filesystem folder. The database V2 (already patched by Step 1) is the sole source of truth — if a track's filename resolves to a different path in the DB, the crate is updated. Previously, custom crates were silently skipped due to a flawed directory-mapping gate.
  - `cdd_sync_crate_fixer.java`: Removed `ExecutorService`, `ConcurrentHashMap`, and multi-value ambiguity logic. Replaced with flat `Map<String, String>` index and single `for` loop.
  - `cdd_sync_main.java`: Added explicit log messages when Step 1 or Step 2 are skipped so the pipeline is never silently bypassed.
  - `cdd_sync_crate.java`: Removed unused `listCrateFiles(File)` helper.

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
