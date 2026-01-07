# ser-sync-pro

Serato crate synchronization tool — automatically sync your filesystem folders to Serato crates.

## License

[GNU GPL v3](http://www.gnu.org/licenses/gpl.html)

Based on [serato-sync](https://github.com/ralekseenkov/serato-sync-old/) by Roman Alekseenkov.

## Features

- **Folder → Crate Mapping**: Mirror your directory structure directly to Serato crates
- **Smart Deduplication**: Prevents duplicate tracks using Unicode-aware filename matching
- **Pre-Sync Backup**: Automatically backs up `_Serato_` folder with preserved timestamps
- **Parent Crate Support**: Add synced folders as subcrates under existing Serato crates
- **Alphabetical Crate Sorting**: Automatically sort crates A–Z in Serato via `neworder.pref`
- **Duplicate File Scanner**: Logs duplicate files (same name + size) to `ser-sync-dupe-files.log`
- **Auto-Create Missing Folders**: Prompts to create `_Serato_` or parent crate if missing
- **Broken Filepath Fixer**: Automatically repairs broken track paths in existing crates and database V2

## Quick Start

1. Download the `distr/ser-sync-pro/` directory
2. Edit `ser-sync.properties` to configure your paths:

   ```properties
   music.library.filesystem=/path/to/your/music
   music.library.database=/Volumes/YourDrive/_Serato_
   ```

3. Run: `java -jar ser-sync-pro.jar`

## Configuration Options

| Property | Description | Default |
| -------- | ----------- | ------- |
| `mode` | `gui` or `cmd` | `gui` |
| `music.library.filesystem` | Path to your music content (not _Serato_ folder) | Required |
| `music.library.database` | Path to `_Serato_` folder | Required |
| `music.library.database.backup` | Create backup before sync | `true` |
| `music.library.database.clear-before-sync` | Clear existing crates first | `false` |
| `crate.parent.path` | Parent crate for synced folders | None |
| `crate.fix.broken.paths` | Fix broken filepaths in crates & database | `false` |
| `crate.skip.existing.tracks` | Skip tracks already in Serato | `true` |
| `crate.dedupe.mode` | `filename`, `path`, or `off` | `filename` |
| `crate.sorting.alphabetical` | Sort crates A–Z in Serato | `false` |
| `harddrive.dupe.scan.enabled` | Log duplicate files on disk | `false` |

## Building from Source

### 1. Install Dependencies

Requires **Java 11** and **Apache Ant**.

#### macOS (using [Homebrew](https://brew.sh/))

```bash
brew install openjdk@11 ant
```

### 2. Compile and Package

```bash
ant all
```

Output: `distr/ser-sync-pro/ser-sync-pro.jar`

## Project Structure

```text
ser-sync-pro/
├── src/                    # Java source files
│   ├── ser_sync_main.java          # Entry point
│   ├── ser_sync_crate.java         # Crate file reader/writer
│   ├── ser_sync_crate_fixer.java   # Broken path fixer
│   ├── ser_sync_database.java      # Database V2 parser
│   ├── ser_sync_database_fixer.java # Database path updater
│   └── ...
├── build.xml               # Ant build script
├── out/                    # Compiled classes (generated)
├── distr/                  # Distribution artifacts (generated)
└── README.md
```

## How It Works

1. **Backup**: Creates timestamped backup of `_Serato_` folder
2. **Scan**: Reads your music library directory structure
3. **Fix Paths** (optional): Repairs broken filepaths in existing crates and updates database V2
4. **Deduplicate**: Skips tracks already in Serato database
5. **Build Crates**: Creates `.crate` files mirroring your folder structure
6. **Sort** (optional): Generates `neworder.pref` for alphabetical crate ordering
