# cdd-sync-pro Codebase Guide

This document provides a comprehensive overview of the **cdd-sync-pro** repository structure, modules, entry points, and dependencies. Use this as a reference for navigating and understanding the codebase.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Directory Structure](#directory-structure)
3. [Major Modules](#major-modules)
4. [Module Details](#module-details)
5. [Configuration Files](#configuration-files)
6. [Serato Data Reference](#serato-data-reference)
7. [Build System](#build-system)
8. [Key Data Flows](#key-data-flows)

---

## Project Overview

**cdd-sync-pro** is a Serato DJ crate synchronization tool that:

- Mirrors filesystem directory structures to Serato crates
- Fixes broken file paths in crates and session files
- Provides smart deduplication and backup functionality
- Supports alphabetical crate sorting

The project contains **two distinct Java applications** and one **Python companion tool**:

1. **cdd-sync-pro** — Main sync tool (filesystem → Serato crates)
2. **session-fixer** — Standalone tool to repair session file paths
3. **s3-smart-sync** — S3 sync companion (Python, separate silo)

---

## Directory Structure

```text
cdd-sync-pro/
├── shared/src/                         # Shared Java source (used by both apps, 12 files)
├── cdd-sync-pro/src/                   # Main sync app source (12 files)
│   └── cdd-sync.properties.template    # Config template for main sync tool
├── session-fixer/                      # Session-fixer silo (standalone tool)
│   ├── src/                            # Session-fixer source files (4 files)
│   ├── CODEBASE_GUIDE.md               # Developer docs for session-fixer
│   ├── README.md                       # User docs for session-fixer
│   └── session-fixer.properties.template
├── s3-smart-sync/                      # S3 sync companion (Python)
├── test/                               # JUnit 5 unit tests
├── lib/                                # Test dependencies (JUnit platform JAR)
├── md/                                 # Internal docs (CODEBASE_GUIDE, CHANGELOG, TODO, etc.)
│   └── actions/                        # Phased action plans + audit trails
├── build.xml                           # Apache Ant build script (compiles all 3 Java source dirs)
├── distr/                              # Distribution artifacts (generated)
├── out/                                # Compiled classes (generated)
└── README.md                           # User documentation
```

---

## Major Modules

| Module | Source Dir | Entry Point | Purpose | Key Dependencies |
|--------|-----------|-------------|---------|------------------|
| **Main Sync** | `cdd-sync-pro/src/` | `cdd_sync_main.java` | Syncs filesystem to Serato crates | `cdd_sync_config`, `cdd_sync_media_library`, `cdd_sync_library`, `cdd_sync_crate` |
| **GUI Window** | `cdd-sync-pro/src/` | `cdd_sync_pro_window.java` | Dark-themed config + log window | `cdd_sync_log_window`, `cdd_sync_config` |
| **Session Fixer** | `session-fixer/src/` | `session_fixer_main.java` | [See session-fixer/CODEBASE_GUIDE.md](../session-fixer/CODEBASE_GUIDE.md) | Uses shared modules |
| **Crate Management** | `cdd-sync-pro/src/` | `cdd_sync_crate.java` | Read/write Serato `.crate` files | `cdd_sync_input_stream`, `cdd_sync_output_stream`, `cdd_sync_database` |
| **Database Parser** | `shared/src/` | `cdd_sync_database.java` | Parse Serato `database V2` file | — |
| **Media Library** | `shared/src/` | `cdd_sync_media_library.java` | Scan filesystem for audio/video files | — |
| **Path Fixers** | `cdd-sync-pro/src/` + `shared/src/` | `cdd_sync_crate_fixer.java`, `cdd_sync_database_fixer.java` | Repair broken paths | `cdd_sync_media_library`, `cdd_sync_database` |
| **I/O Utilities** | `shared/src/` | `cdd_sync_input_stream.java`, `cdd_sync_output_stream.java` | Binary stream helpers for Serato format | — |
| **Logging** | `shared/src/` | `cdd_sync_log.java` | GUI/CLI logging, file output | `cdd_sync_log_window` |

---

## Module Details

### 1. Main Entry Point (`cdd-sync-pro/src/`)

#### `cdd_sync_main.java`

- **Purpose**: Main entry point for the crate sync application
- **Modes**:
  - **GUI mode** (default): Launches `cdd_sync_pro_window` config panel, runs sync on SwingWorker background thread
  - **CLI mode** (`mode=cmd`): Reads `cdd-sync.properties` and runs sync directly
  - **Dry-run mode** (`--dry-run` arg, CLI only): Full sync logic runs but all 7 write sites are suppressed; each logs `[DRY RUN] Would have: ...`
- **Dependencies**:
  - `cdd_sync_config` — Load configuration
  - `cdd_sync_pro_window` — Interactive config GUI (GUI mode only)
  - `cdd_sync_backup` — Create backups
  - `cdd_sync_media_library` — Scan filesystem
  - `cdd_sync_library` — Build crate hierarchy
  - `cdd_sync_crate_fixer` — Fix broken paths
  - `cdd_sync_track_index` — Deduplication
  - `cdd_sync_pref_sorter` — Crate sorting
- **Key Methods**:
  - `main(String[] args)` — Entry point, branches to GUI or CLI
  - `launchGui(config)` — Show config window, wire Start callback
  - `runSync(config)` — Core sync logic (shared by both modes)
  - `scanForHardDriveDuplicates()` — Log duplicate files

#### `cdd_sync_pro_window.java`

- **Purpose**: Dark-themed interactive config + log GUI window
- **Extends**: `cdd_sync_log_window` (shared base class)
- **Features**:
  - Path fields with Browse buttons (JFileChooser)
  - Sync Options panel: backup, skip existing, fix broken paths, sort crates, dedup mode
  - Duplicate Management panel: scan toggle, detection mode, move mode
  - Log Output area (dark JTextArea)
  - Start/Cancel buttons with state management
  - Loads from / saves to `cdd-sync.properties`
- **Theme**: Cross-platform Metal L&F with dark colors (`#3C3F41` background, `#1E1E1E` log area)

> **Session Fixer**: Standalone silo at `session-fixer/src/`. See [session-fixer/CODEBASE_GUIDE.md](../session-fixer/CODEBASE_GUIDE.md) for details.

---

### 2. Configuration (`cdd-sync-pro/src/`)

#### `cdd_sync_config.java`

- **Purpose**: Loads settings from `cdd-sync.properties` or from GUI-provided Properties
- **Config File**: `cdd-sync.properties` or `cdd-sync.properties.txt`
- **Constructors**:
  - `cdd_sync_config()` — File-based (CLI mode)
  - `cdd_sync_config(Properties)` — Properties-based (GUI mode)
- **Key Methods**:
  - `getProperties()` — Returns raw Properties for GUI pre-population
  - `getMusicLibraryPath()` — Filesystem path to music
  - `getSeratoLibraryPath()` — Path to `_Serato_` folder
  - `getParentCratePath()` — Optional parent crate
  - `isBackupEnabled()` — Backup toggle
  - `isCrateSortingEnabled()` — Alphabetical sorting toggle
  - `isFixBrokenPathsEnabled()` — Path repair toggle
  - `getDedupMode()` — `path`, `filename`, or `off` (controls database duplicate detection)
  - `getDupeDetectionMode()` — `name-only`, `name-and-size`, or `off` (controls hard drive duplicate matching)
  - `getDupeMoveMode()` — `keep-newest`, `keep-oldest`, or `false` (controls which duplicates to move)
  - `isDryRun()` / `setDryRun(boolean)` — Runtime dry-run flag (set by `main()` from `--dry-run` arg; never written to properties)

---

### 3. Crate Management (`cdd-sync-pro/src/`)

#### `cdd_sync_crate.java`

- **Purpose**: Read/write Serato `.crate` binary files
- **Dependencies**:
  - `cdd_sync_input_stream`, `cdd_sync_output_stream` — Binary I/O
  - `cdd_sync_database` — Path encoding lookup
- **Binary Format**: UTF-16BE encoded, `vrsn`/`otrk`/`ptrk` tags
- **Key Methods**:
  - `readFrom(File)` — Parse existing crate file
  - `writeTo(File)` — Write crate to disk
  - `addTrack(String)` / `addTracks(Collection)` — Add tracks
  - `setDatabase(cdd_sync_database)` — Set path encoding reference

#### `cdd_sync_library.java`

- **Purpose**: Build Serato crate hierarchy from filesystem structure
- **Dependencies**: `cdd_sync_crate`, `cdd_sync_media_library`, `cdd_sync_track_index`
- **Key Methods**:
  - `createFrom(cdd_sync_media_library, parentCratePath, trackIndex)` — Build crate tree
  - `writeTo(seratoPath, clearFirst)` — Smart write: checks existing crate content on disk and only updates if changed
  - `getAllCrateNames()` — Get crate names for sorting

---

### 4. Database Parsing (`shared/src/`)

#### `cdd_sync_database.java`

- **Purpose**: Parse Serato `database V2` file
- **Binary Format**: `vrsn` header, `otrk` blocks, `pfil`/`tsiz` tags
- **Key Methods**:
  - `readFrom(String)` — Parse database file
  - `containsTrackByPath(trackPath, fileSize)` — Path-based lookup
  - `containsTrackByFilename(trackPath, fileSize)` — Filename-based lookup
  - `getOriginalPathByFilename(trackPath)` — Get exact database encoding

> **Session Parsing**: In `session-fixer/src/session_fixer_parser.java`. See [session-fixer/CODEBASE_GUIDE.md](../session-fixer/CODEBASE_GUIDE.md).

---

### 5. Path Fixers (`cdd-sync-pro/src/` + `shared/src/`)

#### `cdd_sync_crate_fixer.java`

- **Purpose**: Fix broken paths in `.crate` files and sync database V2
- **Features**:
  - Scans all crates for broken paths (files that don't exist)
  - Looks up correct path by filename in the media library
  - Uses database path as search key for exact byte matching
  - Syncs database V2 when crate paths differ from database paths (prevents Serato duplicates)
- **Dependencies**: `cdd_sync_media_library`, `cdd_sync_database`, `cdd_sync_database_fixer`
- **Key Methods**:
  - `fixBrokenPaths(seratoPath, library, database)` — Scan and fix all crates + update database

> **Session Fixer**: In `session-fixer/src/session_fixer_core_logic.java`. See [session-fixer/CODEBASE_GUIDE.md](../session-fixer/CODEBASE_GUIDE.md).

#### `cdd_sync_database_fixer.java`

- **Purpose**: Update paths in Serato `database V2` file
- **Performance**: Pre-builds an otrk block index for O(K) parent lookup instead of O(N) per-call scanning. Index is rebuilt only when path length changes cause array resizing.
- **Key Methods**:
  - `updatePath(databasePath, oldPath, newPath)` — Single path update
  - `updatePaths(databasePath, pathMappings)` — Batch update (uses indexed lookup)

---

### 6. Supporting Modules (mixed)

#### `cdd_sync_media_library.java`

- **Purpose**: Scan filesystem for supported audio/video files
- **Supported Formats**: mp3, flac, wav, ogg, aif, aiff, aac, alac, m4a, mov, mp4, avi, flv, mpg, mpeg, dv, qtz
- **Extension Check**: Uses `Set<String>` lookup (replacing 17 compiled regex patterns)
- **Features**: Parallel directory scanning using ForkJoinPool
- **Key Methods**:
  - `readFrom(String)` — Scan directory tree
  - `getTracks()` / `getChildren()` — Get tracks and subdirectories
  - `flattenTracks(List)` — Get all tracks as flat list

#### `cdd_sync_crate_scanner.java`

- **Purpose**: Scan existing `.crate` files for track paths
- **Key Methods**:
  - `scanFrom(seratoPath)` — Parse all crates in Subcrates folder
  - `containsTrackByPath()` / `containsTrackByFilename()` — Lookup methods

#### `cdd_sync_track_index.java`

- **Purpose**: Unified track index for deduplication
- **Dependencies**: `cdd_sync_database`, `cdd_sync_crate_scanner`
- **Modes**: `path`, `filename`, `off`
- **Key Methods**:
  - `createFrom(seratoPath, mode)` — Build index from database + crates
  - `shouldSkipTrack(trackPath, fileSize)` — Check for duplicates

#### `cdd_sync_backup.java`

- **Purpose**: Create timestamped backups of `_Serato_` folder
- **Key Methods**:
  - `createBackup(seratoPath)` — Create full backup

#### `cdd_sync_pref_sorter.java`

- **Purpose**: Sort crates alphabetically via `neworder.pref`
- **Key Methods**:
  - `sort(seratoPath)` — Recreate `neworder.pref` with sorted crates

#### `cdd_sync_dupe_mover.java`

- **Purpose**: Scan for duplicate files and move copies to a timestamped folder
- **Architecture**: Instance-based state. The static `scanAndMoveDuplicates()` API creates an internal instance to avoid re-entrant state bugs from static mutable fields.
- **Move Strategies**:
  - `keep-newest` — Keep newest file (by modification time), move older duplicates
  - `keep-oldest` — Keep oldest file (by modification time), move newer duplicates
- **Detection Strategies**: Supports `name-and-size` (strict) or `name-only` (catches different versions/edits with same name)
- **Output**: `cdd-sync-pro/dupes/<timestamp>/dupes.log` + moved files with preserved paths
- **Flow**: Runs early in sync process (before crate building), triggers library rescan after moving
- **Dependencies**: `cdd_sync_media_library`, `cdd_sync_log`, `cdd_sync_config`
- **Key Methods**:
  - `scanAndMoveDuplicates(musicLibraryRoot, library, detectionMode, moveMode)` — Static entry point (creates instance internally)
  - `processDuplicateGroup()` — Instance method: keeps one file based on moveMode, moves the rest
  - `getRelativePath()` — Calculates relative path for folder structure preservation
  - `writeLogFile()` — Creates log with header at top (date/time, counts)

---

### 7. Utility Classes (`shared/src/`)

#### `cdd_sync_binary_utils.java`

- Shared binary utility methods: `readInt`, `readFile`, `writeFile`, `getFilename`, `getRawFilename`, `formatSize`
- **Path normalization** (consolidated from 4 duplicated implementations):
  - `normalizePath(path)` — Lowercase, NFC, strips volume/drive prefix. For comparison.
  - `normalizePathForDatabase(path)` — Case-preserving, strips volume/drive prefix. For database/crate writes.

#### `cdd_sync_input_stream.java`

- Extended `DataInputStream` for reading Serato binary format
- UTF-16BE string reading, integer/long value parsing

#### `cdd_sync_output_stream.java`

- Extended `DataOutputStream` for writing Serato binary format
- UTF-16 string writing, long value serialization

#### `cdd_sync_log.java` / `cdd_sync_log_window.java` / `cdd_sync_log_window_handler.java`

- Logging with GUI and CLI support
- Configurable log directory via `setLogDirectory()` — defaults to `<volume>/cdd-sync-pro/logs/`
- Timestamped logs to `cdd-sync-pro-<timestamp>.log`
- `cdd_sync_log_window` is the shared base class (protected fields for subclassing)
- `cdd_sync_log_window_handler` — `java.util.logging` handler bridging to the GUI log window; installed via `install()`

#### `cdd_sync_exception.java` / `cdd_sync_fatal_exception.java`

- `cdd_sync_exception` — Recoverable custom exception
- `cdd_sync_fatal_exception` — Unrecoverable custom exception (aborts sync)

#### `cdd_sync_file_utils.java`

- Simple file system utility methods (in `cdd-sync-pro/src/`)

---

## Configuration Files

### `cdd-sync.properties`

```properties
mode=gui                                    # gui or cmd
music.library.filesystem=/path/to/music     # Source music folder
music.library.database=/Volumes/Drive/_Serato_  # Target Serato folder
music.library.database.backup=true          # Enable backup
crate.parent.path=Sync                      # Optional parent crate
database.fix.broken.paths=false             # Fix broken paths
database.skip.existing.tracks=true          # Skip duplicates
database.dupe.detection.mode=filename       # path, filename, or off
crate.sorting.alphabetical=false            # Sort crates A-Z
harddrive.dupe.scan.enabled=true            # Master switch for dupe features
harddrive.dupe.move.enabled=keep-newest     # keep-newest, keep-oldest, or false (requires scan.enabled=true)
harddrive.dupe.detection.mode=name-and-size # name-only, name-and-size, or off (requires scan.enabled=true)
```

### `session-fixer.properties`

```properties
mode=gui                                    # gui or cmd
music.library.filesystem=/path/to/music1, /path/to/music2  # Multiple paths
music.library.serato=~/music/_Serato_       # Must be HOME _Serato_
backup.enabled=true                         # Enable backup
session.min.duration=                       # Min session length (minutes)
```

---

## Serato Data Reference

### Directory Structure (`_Serato_/`)

| Path | Description |
|------|-------------|
| `database V2` | Main Serato track database |
| `Subcrates/` | `.crate` files (one per crate) |
| `History/Sessions/` | `.session` files (play history) |
| `History/history.database` | Session index file |
| `neworder.pref` | Crate sort order configuration |

### Binary File Format

All Serato binary files (crates, database, sessions) share similar structure:

- **Encoding**: UTF-16BE for strings
- **Headers**: 4-byte tag (`vrsn`, `otrk`, `oent`, `pfil`, etc.)
- **Lengths**: Big-endian 4-byte integers

---

## Build System

### Ant Build (`build.xml`)

| Target | Description |
|--------|-------------|
| `ant all` | Clean and build both applications |
| `ant compile` | Compile all source files (Java 11) |
| `ant test` | Compile and run unit tests (JUnit 5, Java 11) |
| `ant jar` | Create cdd-sync-pro.jar |
| `ant session-fixer-jar` | Create session-fixer.jar |
| `ant run` | Build and run cdd-sync-pro |
| `ant session-fixer-run` | Build and run session-fixer |
| `ant clean` | Remove all generated files |

**Quick start:**

```bash
ant all      # Build everything
ant test     # Run all tests (26 tests)
ant run      # Build and run cdd-sync-pro
```

### CI / GitHub Actions

`.github/workflows/build.yml` runs `ant test` on every push and pull request targeting `master`.

- **Runner**: `ubuntu-latest`
- **Java**: Temurin 11 (via `actions/setup-java@v4`)
- **Ant**: Installed via `apt-get`
- **Trigger**: `push` and `pull_request` on `master`

### Test Suite

- `test/cdd_sync_binary_utils_test.java` — Tests for readInt, getFilename, getRawFilename, formatSize, readFile/writeFile, normalizePath, normalizePathForDatabase
- `test/cdd_sync_crate_test.java` — Crate read/write round-trip tests (empty, populated, track order)

### Output Artifacts

- `distr/cdd-sync-pro/cdd-sync-pro.jar` — Main sync tool
- `distr/session-fixer/session-fixer.jar` — Session fixer

---

## Key Data Flows

### Sync Flow (cdd-sync-pro)

```text
cdd_sync_config
       |
       v
cdd_sync_backup ──────────────> <volume>/cdd-sync-pro/backup/
       |
       v
cdd_sync_media_library ───────> Audio/Video files
       |
       v
cdd_sync_dupe_mover ──────────> cdd-sync-pro/dupes/<timestamp>/
       |                        (if harddrive.dupe.move.enabled=true)
       | (removes moved files from library)
       v
cdd_sync_track_index <───────── cdd_sync_database
       |                        cdd_sync_crate_scanner
       v
cdd_sync_library ─────────────> .crate files (no broken paths!)
       |
       v
cdd_sync_pref_sorter ─────────> neworder.pref
```

### Session Fix Flow (session-fixer)

```text
session_fixer_config
       |
       v
cdd_sync_backup ──────────────> Backup created
       |
       v
cdd_sync_media_library ───────> File lookup table
       |
       v
session_fixer_core_logic ─────> Updated .session files
                                 Updated database V2
```

---

## Summary

This codebase contains two main Java applications sharing common infrastructure:

1. **cdd-sync-pro**: Syncs filesystem structure to Serato crates
2. **session-fixer**: Repairs broken paths in Serato session files

**Core shared modules** (`shared/src/`):

- Binary parsers: `cdd_sync_crate`, `cdd_sync_database`
- Session parsing: `session-fixer/src/session_fixer_parser.java` (in session-fixer silo)
- Media scanning: `cdd_sync_media_library`
- Path fixing: `cdd_sync_crate_fixer`, `cdd_sync_database_fixer`
- Utilities: `cdd_sync_backup`, `cdd_sync_log`, `cdd_sync_track_index`

All modules are designed to work with Serato's proprietary binary formats using UTF-16BE encoding.

---

## Known Limitations & Debugging Notes

### Unicode Path Encoding (NFD vs NFC)

**Issue**: Serato's `database V2` stores file paths using **NFD** (Normalized Form Decomposed) Unicode encoding. Characters like `ó` are stored as `o` + combining acute accent (two separate Unicode characters). macOS filesystem typically returns paths in **NFC** (precomposed) format where `ó` is a single character.

**Impact**: Path comparisons fail if not normalized consistently, causing:

- Broken path detection to fail
- Database updates not matching existing entries
- Potential duplicate track entries

**Solution Applied**: Path normalization is now consolidated in `cdd_sync_binary_utils`:

- `normalizePath()` — NFC + lowercase for comparison across sources
- `normalizePathForDatabase()` — Case-preserving for crate/database writes (used by `cdd_sync_crate.getUniformTrackName()` and `cdd_sync_database_fixer`)
- `addTrack()` checks the database for existing filename encoding and uses Serato's original encoding when adding tracks to crates, preventing duplicate database entries from NFC/NFD mismatches

### Serato Duplicate Track Creation

**Issue**: When Serato opens and reads a crate file containing a path that doesn't exactly match an existing `pfil` entry in `database V2`, it creates a **new database entry** instead of updating the existing one.

**Observed Behavior**:

- Track at `Crates/OldFolder/track.mp3` exists in database
- File is moved to `Crates/NewFolder/track.mp3`
- Crate updated to reference new path
- Serato creates NEW entry for `Crates/NewFolder/track.mp3`
- OLD entry remains (orphaned, shows as "broken")

**Current Status**: This appears to be Serato's internal behavior when "Reading Tags" from files. The sync tool updates both crate files and database V2, but Serato may still create duplicates when it scans files through crates.

**Workaround**: Users may need to manually clean up duplicate entries in Serato, or use Serato's "Relocate Lost Files" feature.

### Path Format Requirements

| Component | Required Format |
|-----------|-----------------|
| `.crate` files | Relative path, NFD encoded: `Crates/Folder/file.mp3` |
| `database V2` pfil | Relative path, NFD encoded: `Crates/Folder/file.mp3` |
| Filesystem lookups | Absolute path, NFC encoded: `/Volumes/Drive/Crates/Folder/file.mp3` |

### Smart Crate Writing

To avoid unnecessary disk I/O and redundant log messages, `cdd_sync_library` implements "Smart Crate Writing":

1. Before writing a crate, it reads the existing `.crate` file from disk (if present).
2. It compares the in-memory crate with the on-disk crate using a robust `equals()` method.
3. The `equals()` method normalizes track paths (converting absolute to relative, standardizing separators) to ensure accurate comparison.
4. If the content (tracks, version, sorting, columns) matches, the write is skipped.

### Debugging Path Issues

1. **Check Unicode encoding**: Use Python to inspect character encoding:

   ```python
   from unicodedata import normalize
   path = "..."
   print(f"NFC: {path == normalize('NFC', path)}")
   print(f"NFD: {path == normalize('NFD', path)}")
   ```

2. **Check database entries**: Search for tracks in database V2:

   ```python
   # See temp/dirs/ for example analysis scripts
   ```

3. **Enable debug logging**: The path fixer logs:
   - `"Path fixes to apply: N"` — Number of broken paths detected
   - `"Fixed broken path in 'crate.crate'"` — Individual fixes
   - `"Updated N paths in database V2"` — Database updates applied
