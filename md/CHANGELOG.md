# Changelog — ser-sync-pro

All notable changes to this project will be documented in this file.

---

## [Unreleased]

- **Silo restructure**: Moved source files into `shared/src/` (9 shared), `ser-sync-pro/src/` (11 app-only), and `session-fixer/src/` (4, unchanged). Updated `build.xml` to compile from all three directories.

## Past Highlights (from Git history)

- **dc8b11a** — Create `dependabot.yml` for automated dependency updates
- **0a8ee2f** — Add detailed guide for the Serato `.session` file format
- **cc94a8a** — Introduce batch synchronization mode with config file support and server-side rename detection
- **b69bfe7** — Allow date-based selection of Serato database entries when fixing duplicate broken paths in crates
- **982ac64** — Refactor: Move session-fixer to standalone silo
