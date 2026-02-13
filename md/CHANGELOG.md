# Changelog — ser-sync-pro

All notable changes to this project will be documented in this file.

---

## [Unreleased]

- **Silo restructure**: Moved source files into `shared/src/` (9 shared), `ser-sync-pro/src/` (11 app-only), and `session-fixer/src/` (4, unchanged). Updated `build.xml` to compile from all three directories.
- **Bug fix**: Smart crate write always rewrote all 138 crates due to `Collections.unmodifiableCollection()` not overriding `equals()`. Changed `getColumns()` to return `unmodifiableList()` for proper element comparison.
- **Logs to volume**: Log files now write to `<volume>/ser-sync-pro/logs/` alongside backup and dupes instead of CWD-relative `logs/`.
- **GUI config window**: New interactive dark-themed config panel (`ser_sync_pro_window.java`) with path fields + Browse buttons, sync option checkboxes, duplicate management dropdowns, log output area, and Start/Cancel buttons. Runs sync on SwingWorker background thread. Settings persist to `ser-sync.properties`. Session-fixer unaffected (Option A architecture).

## Past Highlights (from Git history)

- **dc8b11a** — Create `dependabot.yml` for automated dependency updates
- **0a8ee2f** — Add detailed guide for the Serato `.session` file format
- **cc94a8a** — Introduce batch synchronization mode with config file support and server-side rename detection
- **b69bfe7** — Allow date-based selection of Serato database entries when fixing duplicate broken paths in crates
- **982ac64** — Refactor: Move session-fixer to standalone silo
