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
- **Alphabetical Crate Sorting**: Automatically sort crates A–Z in Serato
- **Duplicate File Scanner**: Logs duplicate files (same name + size) to `ser-sync-dupe-files.log`
- **Auto-Create Missing Folders**: Prompts to create `_Serato_` or parent crate if missing

## Quick Start

1. Download `ser-sync-pro.jar` or 'distr/ser-sync-pro/' directory.
2. Create or Edit the existing `ser-sync.properties` in the same directory as `ser-sync-pro.jar` or 'distr/ser-sync-pro/':

## Configuration Options

| Property | Description | Default |
|----------|-------------|---------|
| `mode` | `gui` or `cmd` | `gui` |
| `music.library.filesystem` | Path to your music folder | Required |
| `music.library.ser-sync` | Path to `_Serato_` folder | Required |
| `music.library.ser-sync.clear-before-sync` | Clear existing crates first | `false` |
| `crate.parent.path` | Parent crate for synced folders | None |
| `skip.existing.tracks` | Skip tracks already in Serato | `true` |
| `dedup.mode` | `filename`, `path`, or `off` | `filename` |
| `backup.enabled` | Create backup before sync | `true` |
| `harddrive.dupe.scan.enabled` | Log duplicate files on disk | `false` |
| `crate.sorting.alphabetical` | Sort crates A–Z in Serato | `false` |

1. Run: `java -jar ser-sync-pro.jar`

## Requirements, Building from Source

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

```
ser-sync-pro/
├── src/           # Source files
├── build.xml      # Ant build script
├── out/           # Compiled classes (generated)
├── distr/         # Distribution artifacts (generated)
└── README.md
```
