# ser-sync-pro Codebase Guide

This document provides a comprehensive overview of the **ser-sync-pro** repository structure, modules, entry points, and dependencies. Use this as a reference for navigating and understanding the codebase.

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

**ser-sync-pro** is a Serato DJ crate synchronization tool that:

- Mirrors filesystem directory structures to Serato crates
- Fixes broken file paths in crates and session files
- Provides smart deduplication and backup functionality
- Supports alphabetical crate sorting

The project contains **two distinct applications**:

1. **ser-sync-pro** — Main sync tool (filesystem → Serato crates)
2. **session-fixer** — Standalone tool to repair session file paths

---

## Directory Structure

```text
ser-sync-pro/
├── src/                                # Main app Java source files (19 files)
├── session-fixer/                      # Session-fixer silo (standalone tool)
│   ├── src/                            # Session-fixer source files (4 files)
│   ├── CODEBASE_GUIDE.md               # Developer docs for session-fixer
│   ├── README.md                       # User docs for session-fixer
│   └── session-fixer.properties.template
├── build.xml                           # Apache Ant build script
├── ser-sync.properties.template        # Config template for main sync tool
├── distr/                              # Distribution artifacts (generated)
├── out/                                # Compiled classes (generated)
└── README.md                           # User documentation
```

---

## Major Modules

| Module | Entry Point | Purpose | Key Dependencies |
|--------|-------------|---------|------------------|
| **Main Sync** | `ser_sync_main.java` | Syncs filesystem to Serato crates | `ser_sync_config`, `ser_sync_media_library`, `ser_sync_library`, `ser_sync_crate` |
| **Legacy Entry** | `Main.java` | Legacy entry point (redirects to main) | `ser_sync_main` |
| **Session Fixer** | `session-fixer/src/` | [See session-fixer/CODEBASE_GUIDE.md](session-fixer/CODEBASE_GUIDE.md) | Standalone silo |
| **Crate Management** | `ser_sync_crate.java` | Read/write Serato `.crate` files | `ser_sync_input_stream`, `ser_sync_output_stream`, `ser_sync_database` |
| **Database Parser** | `ser_sync_database.java` | Parse Serato `database V2` file | — |
| **Media Library** | `ser_sync_media_library.java` | Scan filesystem for audio/video files | — |
| **Path Fixers** | `ser_sync_crate_fixer.java`, `ser_sync_database_fixer.java` | Repair broken paths | `ser_sync_media_library`, `ser_sync_database` |
| **I/O Utilities** | `ser_sync_input_stream.java`, `ser_sync_output_stream.java` | Binary stream helpers for Serato format | — |
| **Logging** | `ser_sync_log.java` | GUI/CLI logging, file output | `ser_sync_log_window` |

---

## Module Details

### 1. Main Entry Points

#### `ser_sync_main.java` (Primary Entry Point)

- **Purpose**: Main entry point for the crate sync application
- **Dependencies**:
  - `ser_sync_config` — Load configuration
  - `ser_sync_backup` — Create backups
  - `ser_sync_media_library` — Scan filesystem
  - `ser_sync_library` — Build crate hierarchy
  - `ser_sync_crate_fixer` — Fix broken paths
  - `ser_sync_track_index` — Deduplication
  - `ser_sync_pref_sorter` — Crate sorting
- **Key Methods**:
  - `main(String[] args)` — Entry point
  - `scanForHardDriveDuplicates()` — Log duplicate files

#### `Main.java` (Legacy Entry Point)

- **Purpose**: Legacy redirect to `ser_sync_main`
- **Dependencies**: Same as `ser_sync_main`

> **Session Fixer**: Moved to standalone silo at `session-fixer/src/`. See [session-fixer/CODEBASE_GUIDE.md](session-fixer/CODEBASE_GUIDE.md) for details.

---

### 2. Configuration

#### `ser_sync_config.java`

- **Purpose**: Loads settings from `ser-sync.properties`
- **Config File**: `ser-sync.properties` or `ser-sync.properties.txt`
- **Key Methods**:
  - `getMusicLibraryPath()` — Filesystem path to music
  - `getSeratoLibraryPath()` — Path to `_Serato_` folder
  - `getParentCratePath()` — Optional parent crate
  - `isBackupEnabled()` — Backup toggle
  - `isCrateSortingEnabled()` — Alphabetical sorting toggle
  - `isFixBrokenPathsEnabled()` — Path repair toggle
  - `getDedupMode()` — `path`, `filename`, or `off` (controls database duplicate detection)
  - `getDupeDetectionMode()` — `name-only`, `name-and-size`, or `off` (controls hard drive duplicate matching)
  - `getDupeMoveMode()` — `keep-newest`, `keep-oldest`, or `false` (controls which duplicates to move)

---

### 3. Crate Management

#### `ser_sync_crate.java`

- **Purpose**: Read/write Serato `.crate` binary files
- **Dependencies**:
  - `ser_sync_input_stream`, `ser_sync_output_stream` — Binary I/O
  - `ser_sync_database` — Path encoding lookup
- **Binary Format**: UTF-16BE encoded, `vrsn`/`otrk`/`ptrk` tags
- **Key Methods**:
  - `readFrom(File)` — Parse existing crate file
  - `writeTo(File)` — Write crate to disk
  - `addTrack(String)` / `addTracks(Collection)` — Add tracks
  - `setDatabase(ser_sync_database)` — Set path encoding reference

#### `ser_sync_library.java`

- **Purpose**: Build Serato crate hierarchy from filesystem structure
- **Dependencies**: `ser_sync_crate`, `ser_sync_media_library`, `ser_sync_track_index`
- **Key Methods**:
  - `createFrom(ser_sync_media_library, parentCratePath, trackIndex)` — Build crate tree
  - `writeTo(seratoPath, clearFirst)` — Smart write: checks existing crate content on disk and only updates if changed
  - `getAllCrateNames()` — Get crate names for sorting

---

### 4. Database Parsing

#### `ser_sync_database.java`

- **Purpose**: Parse Serato `database V2` file
- **Binary Format**: `vrsn` header, `otrk` blocks, `pfil`/`tsiz` tags
- **Key Methods**:
  - `readFrom(String)` — Parse database file
  - `containsTrackByPath(trackPath, fileSize)` — Path-based lookup
  - `containsTrackByFilename(trackPath, fileSize)` — Filename-based lookup
  - `getOriginalPathByFilename(trackPath)` — Get exact database encoding

> **Session Parsing**: Moved to `session-fixer/src/session_fixer_parser.java`. See [session-fixer/CODEBASE_GUIDE.md](session-fixer/CODEBASE_GUIDE.md).

---

### 5. Path Fixers

#### `ser_sync_crate_fixer.java`

- **Purpose**: Fix broken paths in `.crate` files and sync database V2
- **Features**:
  - Scans all crates for broken paths (files that don't exist)
  - Looks up correct path by filename in the media library
  - Uses database path as search key for exact byte matching
  - Syncs database V2 when crate paths differ from database paths (prevents Serato duplicates)
- **Dependencies**: `ser_sync_media_library`, `ser_sync_database`, `ser_sync_database_fixer`
- **Key Methods**:
  - `fixBrokenPaths(seratoPath, library, database)` — Scan and fix all crates + update database

> **Session Fixer**: Moved to `session-fixer/src/session_fixer_core_logic.java`. See [session-fixer/CODEBASE_GUIDE.md](session-fixer/CODEBASE_GUIDE.md).

#### `ser_sync_database_fixer.java`

- **Purpose**: Update paths in Serato `database V2` file
- **Key Methods**:
  - `updatePath(databasePath, oldPath, newPath)` — Single path update
  - `updatePaths(databasePath, pathMappings)` — Batch update

---

### 6. Supporting Modules

#### `ser_sync_media_library.java`

- **Purpose**: Scan filesystem for supported audio/video files
- **Supported Formats**: mp3, flac, wav, ogg, aif, aiff, aac, alac, m4a, mov, mp4, avi, flv, mpg, mpeg, dv, qtz
- **Features**: Parallel directory scanning using ForkJoinPool
- **Key Methods**:
  - `readFrom(String)` — Scan directory tree
  - `getTracks()` / `getChildren()` — Get tracks and subdirectories
  - `flattenTracks(List)` — Get all tracks as flat list

#### `ser_sync_crate_scanner.java`

- **Purpose**: Scan existing `.crate` files for track paths
- **Key Methods**:
  - `scanFrom(seratoPath)` — Parse all crates in Subcrates folder
  - `containsTrackByPath()` / `containsTrackByFilename()` — Lookup methods

#### `ser_sync_track_index.java`

- **Purpose**: Unified track index for deduplication
- **Dependencies**: `ser_sync_database`, `ser_sync_crate_scanner`
- **Modes**: `path`, `filename`, `off`
- **Key Methods**:
  - `createFrom(seratoPath, mode)` — Build index from database + crates
  - `shouldSkipTrack(trackPath, fileSize)` — Check for duplicates

#### `ser_sync_backup.java`

- **Purpose**: Create timestamped backups of `_Serato_` folder
- **Key Methods**:
  - `createBackup(seratoPath)` — Create full backup

#### `ser_sync_pref_sorter.java`

- **Purpose**: Sort crates alphabetically via `neworder.pref`
- **Key Methods**:
  - `sort(seratoPath)` — Recreate `neworder.pref` with sorted crates

#### `ser_sync_dupe_mover.java`

- **Purpose**: Scan for duplicate files and move copies to a timestamped folder
- **Move Strategies**:
  - `keep-newest` — Keep newest file (by modification time), move older duplicates
  - `keep-oldest` — Keep oldest file (by modification time), move newer duplicates
- **Detection Strategies**: Supports `name-and-size` (strict) or `name-only` (catches different versions/edits with same name)
- **Output**: `ser-sync-pro/dupes/<timestamp>/dupes.log` + moved files with preserved paths
- **Flow**: Runs early in sync process (before crate building), triggers library rescan after moving
- **Internal Safety**: Properly checks for directory existence before attempting to create them (`mkdirs()` fix)
- **Dependencies**: `ser_sync_media_library`, `ser_sync_log`, `ser_sync_config`
- **Key Methods**:
  - `scanAndMoveDuplicates(musicLibraryRoot, library, detectionMode, moveMode)` — Main entry point
  - `processDuplicateGroup()` — Keeps one file based on moveMode, moves the rest
  - `getRelativePath()` — Calculates relative path for folder structure preservation
  - `writeLogFile()` — Creates log with header at top (date/time, counts)

---

### 7. Utility Classes

#### `ser_sync_input_stream.java`

- Extended `DataInputStream` for reading Serato binary format
- UTF-16BE string reading, integer/long value parsing

#### `ser_sync_output_stream.java`

- Extended `DataOutputStream` for writing Serato binary format
- UTF-16 string writing, long value serialization

#### `ser_sync_log.java` / `ser_sync_log_window.java`

- Logging with GUI and CLI support
- Timestamped logs to `logs/ser-sync-pro-<timestamp>.log`
- Duplicate file logging to `ser-sync-dupe-files.log`

#### `ser_sync_file_utils.java`

- Simple file utility methods

#### `ser_sync_exception.java`

- Custom exception class

---

## Configuration Files

### `ser-sync.properties`

```properties
mode=gui                                    # gui or cmd
music.library.filesystem=/path/to/music     # Source music folder
music.library.database=/Volumes/Drive/_Serato_  # Target Serato folder
music.library.database.backup=true          # Enable backup
crate.parent.path=Sync                      # Optional parent crate
database.fix.broken.paths=false             # Fix broken paths
database.skip.existing.tracks=true            # Skip duplicates
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

### Directory Structure (`temp/symlinks/_serato_/`)

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
| `ant compile` | Compile all source files |
| `ant jar` | Create ser-sync-pro.jar |
| `ant session-fixer-jar` | Create session-fixer.jar |
| `ant run` | Build and run ser-sync-pro |
| `ant session-fixer-run` | Build and run session-fixer |
| `ant clean` | Remove all generated files |

**Quick start:**

```bash
ant all      # Build everything
ant run      # Build and run ser-sync-pro
```

### Output Artifacts

- `distr/ser-sync-pro/ser-sync-pro.jar` — Main sync tool
- `distr/session-fixer/session-fixer.jar` — Session fixer

---

## Key Data Flows

### Sync Flow (ser-sync-pro)

```text
ser_sync_config
       |
       v
ser_sync_backup ──────────────> ser-sync-pro/backup/
       |
       v
ser_sync_media_library ───────> Audio/Video files
       |
       v
ser_sync_dupe_mover ──────────> ser-sync-pro/dupes/<timestamp>/
       |                        (if harddrive.dupe.move.enabled=true)
       | (removes moved files from library)
       v
ser_sync_track_index <───────── ser_sync_database
       |                        ser_sync_crate_scanner
       v
ser_sync_library ─────────────> .crate files (no broken paths!)
       |
       v
ser_sync_pref_sorter ─────────> neworder.pref
```

### Session Fix Flow (session-fixer)

```text
session_fixer_config
       |
       v
ser_sync_backup ──────────────> Backup created
       |
       v
ser_sync_media_library ───────> File lookup table
       |
       v
ser_sync_session_fixer ───────> Updated .session files
                                Updated database V2
```

---

## Summary

This codebase contains two main applications sharing common infrastructure:

1. **ser-sync-pro**: Syncs filesystem structure to Serato crates
2. **session-fixer**: Repairs broken paths in Serato session files

**Core shared modules**:

- Binary parsers: `ser_sync_crate`, `ser_sync_database`
- Session parsing: `session-fixer/src/session_fixer_parser.java` (in session-fixer silo)
- Media scanning: `ser_sync_media_library`
- Path fixing: `ser_sync_crate_fixer`, `ser_sync_database_fixer`
- Utilities: `ser_sync_backup`, `ser_sync_log`, `ser_sync_track_index`

All modules are designed to work with Serato's proprietary binary formats using UTF-16BE encoding.

---

## Known Limitations & Debugging Notes

### Unicode Path Encoding (NFD vs NFC)

**Issue**: Serato's `database V2` stores file paths using **NFD** (Normalized Form Decomposed) Unicode encoding. Characters like `ó` are stored as `o` + combining acute accent (two separate Unicode characters). macOS filesystem typically returns paths in **NFC** (precomposed) format where `ó` is a single character.

**Impact**: Path comparisons fail if not normalized consistently, causing:

- Broken path detection to fail
- Database updates not matching existing entries
- Potential duplicate track entries

**Solution Applied** (in `ser_sync_crate.java`, `ser_sync_crate_fixer.java`, `ser_sync_database_fixer.java`):

- Use `java.text.Normalizer` to normalize paths
- Crate paths written using **NFD** to match database format
- Path comparisons use **NFC** normalization for consistent matching (implemented in `ser_sync_crate.equals()`)
- `addTrack()` now checks the database for existing filename encoding and uses Serato's original encoding when adding tracks to crates, preventing duplicate database entries from NFC/NFD mismatches

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

To avoid unnecessary disk I/O and redundant log messages, `ser_sync_library` implements "Smart Crate Writing":

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
