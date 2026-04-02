# Technical Migration Plan: Java to Python

This plan outlines the architecture and phased approach for rewriting `cdd-sync-pro` from Java to a modern Python 3.12+ stack, built for the "Builder" agent. 

Per your instructions, this is a **no code** architectural blueprint.

## User Review Required

> [!IMPORTANT]
> - **`serato-tools` capability:** The Python ecosystem has `serato-tools` packages, but they may not exactly match the granular binary precision achieved in your custom `cdd_sync_crate.java` (e.g., column width preservation via raw `ovct`/`osrt` TLV payload passthrough). The Builder agent will need to verify if `serato-tools` supports this or if we need to supplement it with custom `struct` logic.
> - **Flet limitations:** Flet is excellent for fast GUI development and native OS feel, but we need to ensure its asynchronous updates handle the real-time "Live Log" streaming efficiently without blocking the main Python execution thread.

## Target Architecture

### Tech Stack & Dependencies
* **Core Language:** Python 3.12+
* **Binary Parsing:** `serato-tools` (for Tag-Length-Value parsing of `.crate` and `database V2`).
* **GUI Framework:** `flet` (for native macOS Dark Mode and San Francisco font aesthetics).
* **Configuration:** `PyYAML` (replacing `.properties` with `config.yaml`).
* **Packaging:** `pyproject.toml` (Poetry or standard pip requirements).

### Module Structure

We will restructure the current Java silos (now located in `java/`) into cleanly separated Python packages residing entirely within the new `python/` directory:

#### 1. `python/core/` (Replaces `java/shared/src/`)
* **`binary_utils.py`:** Replaces `cdd_sync_binary_utils.java`. Uses Python's `struct` module for big-endian I/O.
* **`path_utils.py`:** Consolidates Unicode normalization using Python's `unicodedata` (`unicodedata.normalize('NFC', ...)` for matching, `NFD` for database values).
* **`serato_parser.py`:** Wrapper around `serato-tools` to handle the specific idiosyncrasies of `database V2` and crate binary parsing.

#### 2. `python/sync/` (Replaces `java/cdd-sync-pro/src/` core logic)
* **`pipeline.py`:** The heart of the application. Replaces `cdd_sync_main.java` and maps the distinct 4-step pipeline (Fix DB Paths -> Fix Existing Crates -> Append Tracks -> Create Crates).
* **`deduplication.py`:** Implements the unicode-aware filename dedup and hard drive duplicate management (`cdd_sync_dupe_mover.java`).
* **`backup.py`:** Implements the pre-sync timestamped backups. Will use a Python `@require_backup` decorator wrapped around any write operations targeting the `_Serato_` folder.
* **`session_fixer.py`:** Absorbs the isolated `session-fixer/` silo (`java/session-fixer/`) to fix broken links in `.session` history files.

#### 3. `python/ui/`
* **`dashboard.py`:** Replaces `cdd_sync_pro_window.java`. A Flet dashboard implementing a 'Scan > Review > Patch' workflow.
* Contains modular views/panels: Backup Options, Directory Mapping, Pipeline Toggles, and a Live Log surface.

## Migration Phases (For the Builder Agent)

> [!CAUTION]
> **STRICT READ-ONLY WARNING:** The `java/` directory contains the production-working legacy Java application. The Builder agent must **NOT** under any circumstances modify, rewrite, or delete any files inside the `java/` directory. All Java source files must be treated as **READ-ONLY REFERENCES** only. All new code generation and file modifications must occur exclusively inside the `python/` directory.

### Phase 1: Foundation & Configuration
* Initialize `python/pyproject.toml` / `python/requirements.txt`.
* Implement the new `config.yaml` schema to map properties like `music.library.filesystem` and `database.dupe.detection.mode`.
* Implement the `python/core/path_utils.py` logic to guarantee 100% parity with the Java NFC/NFD behavior.

### Phase 2: Binary Interoperability & Safety
* Integrate `serato-tools` and build the `python/core/serato_parser.py`.
* Implement the `@require_backup` decorator to ensure no data loss occurs in the `_Serato_` directory.
* Write unit tests mirroring the 26 existing JUnit tests to verify `struct` binary reads exactly match the Java `DataInputStream` output.

### Phase 3: The 4-Step Pipeline & Resolvers
* Port the broken filepath fixer (`cdd_sync_crate_fixer.java`), preserving the exact logic that resolves NFD paths directly from the database for matching crates.
* Port deduplication logic, ensuring both filename-only matches for Serato crates and size+name matches for hard drive dupes.
* Reimplement `neworder.pref` alphabetization.

### Phase 4: GUI & Orchestration
* Build the Flet `python/ui/dashboard.py`.
* Wire the `sync/` modules into the UI using Python `asyncio` or Threading so the UI remains responsive and the Live Log streams smoothly during intensive disk I/O.
* Ensure macOS Native Dark Mode styling is enforced.

## Verification Plan

### Automated Tests
* The Builder agent must utilize `pytest` to replicate the `test/cdd_sync_binary_utils_test.java` and `test/cdd_sync_crate_test.java` test cases.
* Binary round-trip tests: Parse a crate with Python, write it back to disk, and assert via a byte-for-byte binary diff that it is identical to the Java tool's output.

### Manual Verification
* Run the Python `flet` GUI on macOS to confirm Dark Mode theme, UI responsiveness, and Live Log streaming fidelity.
* Execute a dry-run against a test `_Serato_` folder.
