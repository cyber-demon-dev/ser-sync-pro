# ssync_pro

Serato crate synchronization tool - automatically sync your filesystem folders to Serato crates.

## License

[GNU GPL v3](http://www.gnu.org/licenses/gpl.html)

Based on [serato-sync](https://github.com/ralekseenkov/serato-sync-old/) by Roman Alekseenkov.

## Features

- **Folder → Crate Mapping**: Mirror your directory structure directly to Serato crates
- **Smart Deduplication**: Prevents duplicate tracks using Unicode-aware filename matching
- **Pre-Sync Backup**: Automatically backs up `_Serato_` folder with preserved timestamps
- **Parent Crate Support**: Add synced folders as subcrates under existing Serato crates

## Quick Start

1. Download `ssync_pro.jar`
2. Create `ssync.properties` in the same folder:

```properties
mode=gui
music.library.filesystem=/path/to/your/music
music.library.ssync=/Volumes/DriveName/_Serato_
```

1. Run: `java -jar ssync_pro.jar`

## Configuration Options

| Property | Description | Default |
|----------|-------------|---------|
| `mode` | `gui` or `cmd` | `gui` |
| `music.library.filesystem` | Path to your music folder | Required |
| `music.library.ssync` | Path to `_Serato_` folder | Required |
| `music.library.ssync.clear-before-sync` | Clear existing crates first | `false` |
| `crate.parent.path` | Parent crate for synced folders | None |
| `dedup.mode` | `filename`, `path`, or `off` | `filename` |
| `backup.enabled` | Create backup before sync | `true` |

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

Output: `distr/ssync_pro/ssync_pro.jar`

## Project Structure

```
ssync_pro/
├── src/           # Source files
├── build.xml      # Ant build script
├── out/           # Compiled classes (generated)
├── distr/         # Distribution artifacts (generated)
└── README.md
```
