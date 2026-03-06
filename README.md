# ser-sync-pro

![Build](https://github.com/cyber-demon-dev/ser-sync-pro/actions/workflows/build.yml/badge.svg)

Serato DJ crate synchronization tool — automatically sync your filesystem folders to Serato crates and more.

## License

[GNU GPL v3](http://www.gnu.org/licenses/gpl.html)

Based on [serato-sync](https://github.com/ralekseenkov/serato-sync-old/) by Roman Alekseenkov.

## Features

- **Folder → Crate Mapping**: Mirror your directory structure directly to Serato crates
- **Smart Crate Writing**: Only updates `.crate` files if content has changed, preserving disk I/O
- **Robust Path Normalization**: Intelligently handles Unicode (NFC/NFD) and absolute/relative path differences
- **Smart Deduplication**: Prevents duplicate tracks using Unicode-aware filename matching
- **Pre-Sync Backup**: Automatically backs up `_Serato_` folder with preserved timestamps
- **Parent Crate Support**: Add synced folders as subcrates under existing Serato crates
- **Alphabetical Crate Sorting**: Automatically sort crates A–Z in Serato via `neworder.pref`
- **Duplicate File Scanner**: Logs duplicate files (same name + size) to `logs/ser-sync-dupe-files-<timestamp>.log`
- **Duplicate File Mover**: Moves duplicate files to `ser-sync-pro/dupes/<timestamp>/` (choose to keep newest or oldest)
- **Auto-Create Missing Folders**: Prompts to create `_Serato_` or parent crate if missing
- **Broken Filepath Fixer**: Automatically repairs broken track paths in existing crates and database V2
- **Session Fixer**: Standalone tool to fix broken paths in Serato `.session` history files
- **Dry-Run Mode**: Preview a full sync without writing anything to disk (`--dry-run` CLI flag)
- **Timestamped Logs**: All logs saved to `logs/ser-sync-pro-<timestamp>.log`

## Quick Start

1. Download the `distr/ser-sync-pro/` directory
2. Edit `ser-sync.properties` to configure your paths:

   ```properties
   music.library.filesystem=/path/to/your/music
   music.library.database=/Volumes/YourDrive/_Serato_
   ```

3. Run: `java -jar ser-sync-pro.jar`

## CLI Flags

| Flag          | Mode      | Description                                                                        |
|---------------|-----------|------------------------------------------------------------------------------------|
| *(none)*      | GUI / CLI | Normal sync                                                                        |
| `--dry-run`   | CLI only  | Preview sync — logs all actions with `[DRY RUN]` prefix, writes nothing to disk   |

Example:

```bash
java -jar ser-sync-pro.jar --dry-run
```

## Configuration Options

| Property | Description | Default |
| -------- | ----------- | ------- |
| `mode` | `gui` or `cmd` | `gui` |
| `music.library.filesystem` | Path to your music content (not *Serato* folder) | Required |
| `music.library.database` | Path to `_Serato_` folder | Required |
| `music.library.database.backup` | Create backup before sync | `true` |
| `music.library.database.clear-before-sync` | Clear existing crates first | `false` |
| `crate.parent.path` | Parent crate for synced folders | None |
| `database.fix.broken.paths` | Fix broken filepaths in crates & database | `false` |
| `database.skip.existing.tracks` | Skip tracks already in Serato | `true` |
| `database.dupe.detection.mode` | `filename`, `path`, or `off` | `filename` |
| `crate.sorting.alphabetical` | Sort crates A–Z in Serato | `false` |
| `harddrive.dupe.scan.enabled` | Log duplicate files on disk | `false` |
| `harddrive.dupe.move.enabled` | `keep-newest` (move older), `keep-oldest` (move newer), or `false` | `false` |
| `harddrive.dupe.detection.mode` | Strategy: `name-only`, `name-and-size`, or `off` (default) | `off` |

## Building from Source

### 1. Install Dependencies

Requires **Java 11** and **Apache Ant**.

#### macOS (using [Homebrew](https://brew.sh/))

```bash
brew install openjdk@11 ant
```

### 2. Build Targets

The project uses Apache Ant for building. Available targets:

| Target | Description |
| ------ | ----------- |
| `ant all` | Clean and build both `ser-sync-pro` and `session-fixer` |
| `ant compile` | Compile all Java source files |
| `ant jar` | Package `ser-sync-pro` into a JAR |
| `ant session-fixer-jar` | Package `session-fixer` into a JAR |
| `ant run` | Build and run `ser-sync-pro` |
| `ant session-fixer-run` | Build and run `session-fixer` |
| `ant clean` | Remove all generated build artifacts |

### 3. Output Artifacts

- **Main Sync Tool**: `distr/ser-sync-pro/ser-sync-pro.jar`
- **Session Fixer**: `distr/session-fixer/session-fixer.jar`

## Project Structure

```text
ser-sync-pro/
├── shared/src/                     # Shared Java source files (used by both apps)
│   ├── ser_sync_backup.java        # Timestamped backups of the _Serato_ folder
│   ├── ser_sync_database.java      # Parses the Serato database V2 file
│   ├── ser_sync_database_fixer.java # Updates track paths directly in database V2
│   ├── ser_sync_exception.java     # Custom exception class
│   ├── ser_sync_input_stream.java  # Helper for reading Serato's big-endian format
│   ├── ser_sync_log.java           # Logging utility for console, GUI, and file output
│   ├── ser_sync_log_window.java    # GUI component for real-time logging
│   ├── ser_sync_media_library.java # Scans the filesystem for supported media files
│   └── ser_sync_output_stream.java # Helper for writing Serato's big-endian format
├── ser-sync-pro/src/               # Main sync tool source files (silo)
│   ├── ser_sync_main.java          # Primary entry point for the sync application
│   ├── ser_sync_config.java        # Loads and manages configuration from ser-sync.properties
│   ├── ser_sync_crate.java         # Core logic for reading and writing Serato .crate files
│   ├── ser_sync_crate_fixer.java   # Scans crates and repairs broken track paths
│   ├── ser_sync_crate_scanner.java # Scans existing crates to index tracks for deduplication
│   ├── ser_sync_database_entry_selector.java # Date-based entry selection for path fixes
│   ├── ser_sync_dupe_mover.java    # Scans for duplicate files and moves them to safety
│   ├── ser_sync_file_utils.java    # General file system utility methods
│   ├── ser_sync_library.java       # Builds the crate hierarchy mirroring the filesystem
│   ├── ser_sync_pref_sorter.java   # Manages alphabetical crate sorting via neworder.pref
│   └── ser_sync_track_index.java   # Unified index for track lookups and deduplication
├── session-fixer/src/              # Session-fixer standalone tool (silo)
│   ├── session_fixer_main.java     # Entry point
│   ├── session_fixer_config.java   # Configuration loader
│   ├── session_fixer_core_logic.java # Path fixing logic
│   └── session_fixer_parser.java   # .session file parser
├── build.xml                       # Ant build script
├── out/                            # Compiled classes (generated)
├── distr/                          # Distribution artifacts (generated)
└── README.md
```

## How It Works

1. **Backup**: Creates timestamped backup in `ser-sync-pro/backup/` folder
2. **Scan**: Reads your music library directory structure
3. **Fix Paths** (optional): Repairs broken filepaths in existing crates and updates database V2
4. **Deduplicate**: Skips tracks already in Serato database
5. **Build Crates**: Creates `.crate` files mirroring your folder structure (updates only if changed)
6. **Sort** (optional): Generates `neworder.pref` for alphabetical crate ordering
