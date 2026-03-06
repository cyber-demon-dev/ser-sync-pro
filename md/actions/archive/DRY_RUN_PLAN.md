# Dry-Run CLI Flag — Implementation Plan

> **For:** Another agent to execute.
> **Scope:** Add a `--dry-run` CLI flag that runs the full sync logic but suppresses all writes to disk. Logs every action that *would* have been taken instead.
> **Constraint:** One file created or modified per phase. No deviations. No fallback paths.

---

## Context

| Item | Value |
|------|-------|
| Entry point | `ser-sync-pro/src/ser_sync_main.java` |
| Config class | `ser-sync-pro/src/ser_sync_config.java` |
| Write sites in `runSync()` | Backup (`ser_sync_backup`), dupe mover (`ser_sync_dupe_mover`), database fixer (`ser_sync_database_fixer`), crate library write (`crateLibrary.writeTo`), broken path fixer (`ser_sync_crate_fixer`), crate sorter (`ser_sync_pref_sorter`), parent crate creation |
| Activation | CLI arg `--dry-run` (not a property; never written to `ser-sync.properties`) |
| Applies to | CLI mode only. GUI mode ignores it. |

### Dry-run contract

A dry-run run must:

- Log `[DRY RUN]` as a prefix on every suppressed action line
- Skip **all** writes to disk (no backup, no file moves, no crate writes, no database updates, no pref file)
- Still read from disk normally (scans, database parse, crate parse — all read-only ops are fine)
- Exit cleanly with `[DRY RUN] Sync complete — no files were written.`

---

## Phase 1 — Add `isDryRun()` to `ser_sync_config`

**File:** `ser-sync-pro/src/ser_sync_config.java`

**Action:** Add a `dryRun` boolean field and a getter. The field is set only via a dedicated constructor or setter — not from Properties — because dry-run is a runtime flag, not a config file option.

Add the field after the `private Properties properties;` line (line 15):

```java
private boolean dryRun = false;
```

Add the getter after `isGuiMode()` (after line 47):

```java
public boolean isDryRun() {
    return dryRun;
}

public void setDryRun(boolean dryRun) {
    this.dryRun = dryRun;
}
```

**Verify:** `ser_sync_config` compiles. `isDryRun()` returns `false` by default. `setDryRun(true)` sets it to `true`.

---

## Phase 2 — Parse `--dry-run` arg in `ser_sync_main.main()`

**File:** `ser-sync-pro/src/ser_sync_main.java`

**Action:** Parse the `args` array for `--dry-run` before the mode branch, then pass the flag into the config object. Two targeted edits to this file only.

**Edit 1:** Replace the `main()` signature and opening logic (lines 13–31) with:

```java
public static void main(String[] args) {
    boolean dryRunFlag = false;
    for (String arg : args) {
        if ("--dry-run".equals(arg)) {
            dryRunFlag = true;
        }
    }

    // Try to load config to check mode
    ser_sync_config initialConfig;
    try {
        initialConfig = new ser_sync_config();
    } catch (IOException e) {
        // No config file — default to GUI mode
        initialConfig = null;
    }

    if (dryRunFlag && initialConfig != null) {
        initialConfig.setDryRun(true);
    }

    boolean guiMode = (initialConfig == null) || initialConfig.isGuiMode();

    if (guiMode) {
        launchGui(initialConfig);
    } else {
        runSync(initialConfig);
        System.exit(0);
    }
}
```

**Verify:** `main()` compiles. Passing `--dry-run` sets `config.isDryRun()` to `true`. GUI mode is unaffected.

---

## Phase 3 — Guard all write sites in `runSync()`

**File:** `ser-sync-pro/src/ser_sync_main.java`

**Action:** Wrap every disk-write call in `runSync()` with a dry-run guard. Do this in a single edit pass covering all sites. The pattern for every guard is:

```java
if (config.isDryRun()) {
    ser_sync_log.info("[DRY RUN] Would have: <description>");
} else {
    // original call
}
```

Apply guards to the following six sites in `runSync()` in order:

**Site 1 — Backup (lines 104–111):**

```java
if (config.isBackupEnabled()) {
    if (config.isDryRun()) {
        ser_sync_log.info("[DRY RUN] Would have: created backup of " + seratoPath);
    } else {
        String backupPath = ser_sync_backup.createBackup(seratoPath);
        if (backupPath == null) {
            ser_sync_log.error("Backup failed. Aborting sync for safety.");
            ser_sync_log.fatalError();
            return;
        }
    }
}
```

**Site 2 — Dupe mover (lines 128–145):**

```java
if (config.isDupeMoveEnabled()) {
    if (config.isDryRun()) {
        ser_sync_log.info("[DRY RUN] Would have: scanned and moved duplicate files (" + config.getDupeMoveMode() + ")");
    } else {
        java.util.Map<String, String> movedToKept = ser_sync_dupe_mover.scanAndMoveDuplicates(
                config.getMusicLibraryPath(), fsLibrary, config.getDupeDetectionMode(),
                config.getDupeMoveMode());
        if (!movedToKept.isEmpty()) {
            String databasePath = seratoPath + "/database V2";
            int dbUpdated = ser_sync_database_fixer.updatePaths(databasePath, movedToKept);
            if (dbUpdated > 0) {
                ser_sync_log.info("Updated " + dbUpdated + " paths in database V2 for moved duplicates");
            }
            ser_sync_log.info("Rescanning media library after duplicate removal...");
            fsLibrary = ser_sync_media_library.readFrom(config.getMusicLibraryPath());
            ser_sync_log.info("Found " + fsLibrary.getTotalNumberOfTracks() + " tracks remaining.");
        }
    }
}
```

**Site 3 — Serato folder creation (lines 152–170), inside the `if (!new File(seratoPath).isDirectory())` block:**

Replace the `createFolder` branch with:

```java
if (config.isDryRun()) {
    ser_sync_log.info("[DRY RUN] Would have: created Serato library folder " + seratoPath);
} else {
    boolean createFolder = ser_sync_log.confirm(
            "Serato library folder '" + seratoPath + "' does not exist.\n\n" +
                    "Would you like to create it and continue with the sync?");
    if (createFolder) {
        boolean created = new File(seratoPath).mkdirs();
        if (created) {
            ser_sync_log.info("Created Serato library folder: " + seratoPath);
        } else {
            ser_sync_log.error("Failed to create Serato library folder: " + seratoPath);
            ser_sync_log.fatalError();
            return;
        }
    } else {
        ser_sync_log.info("Sync halted by user.");
        ser_sync_log.fatalError();
        return;
    }
}
```

**Site 4 — Parent crate creation (lines 188–196), inside `if (!parentCrateFile.exists())` block:**

Replace the `try { emptyCrate.writeTo(...) }` block with:

```java
if (config.isDryRun()) {
    ser_sync_log.info("[DRY RUN] Would have: created parent crate " + parentCratePath);
} else {
    try {
        parentCrateFile.getParentFile().mkdirs();
        ser_sync_crate emptyCrate = new ser_sync_crate();
        emptyCrate.writeTo(parentCrateFile);
    } catch (ser_sync_exception e) {
        ser_sync_log.error("Failed to create parent crate '" + parentCratePath + "'");
        ser_sync_log.error(e);
        ser_sync_log.fatalError();
        return;
    }
}
```

**Site 5 — Crate library write (lines 230–237):**

```java
if (config.isDryRun()) {
    ser_sync_log.info("[DRY RUN] Would have: written " + crateLibrary.getAllCrateNames().size() + " crates to " + seratoPath);
} else {
    try {
        crateLibrary.writeTo(seratoPath, config.isClearLibraryBeforeSync());
    } catch (ser_sync_exception e) {
        ser_sync_log.error("Error occurred!");
        ser_sync_log.error(e);
        ser_sync_log.fatalError();
        return;
    }
}
```

**Site 6 — Broken path fixer (lines 240–242):**

```java
if (config.isFixBrokenPathsEnabled()) {
    if (config.isDryRun()) {
        ser_sync_log.info("[DRY RUN] Would have: fixed broken paths in crates and database V2");
    } else {
        ser_sync_crate_fixer.fixBrokenPaths(seratoPath, fsLibrary, database, config.getDupeMoveMode());
    }
}
```

**Site 7 — Crate sorter (lines 257–259):**

```java
if (config.isCrateSortingEnabled()) {
    if (config.isDryRun()) {
        ser_sync_log.info("[DRY RUN] Would have: sorted crates alphabetically in neworder.pref");
    } else {
        ser_sync_pref_sorter.sort(seratoPath);
    }
}
```

**Replace the final `ser_sync_log.success()` line (line 261)** with:

```java
if (config.isDryRun()) {
    ser_sync_log.info("[DRY RUN] Sync complete — no files were written.");
} else {
    ser_sync_log.success();
}
```

**Verify:** `runSync()` compiles. Zero write calls are made when `isDryRun()` is `true`.

---

## Phase 4 — Update `ser-sync.properties.template`

**File:** `ser-sync-pro/ser-sync.properties.template`

**Action:** Add a comment-only block at the bottom of the file documenting the `--dry-run` flag. No new property key — this is documentation only.

Append to end of file:

```properties
# --dry-run
# Pass --dry-run as a command-line argument (CLI mode only) to preview all sync
# actions without writing anything to disk. Logs every action with a [DRY RUN] prefix.
# Example: java -jar ser-sync-pro.jar --dry-run
```

**Verify:** File still loads correctly as a Properties file. No new keys are present.

---

## Phase 5 — Update `md/TODO.md`

**Action:** Edit `md/TODO.md`. Remove the following line from the Backlog section:

```
- [ ] Add `--dry-run` CLI flag to preview changes without writing
```

Append to `## Done` section (create if absent):

```
- [x] Add `--dry-run` CLI flag — previews all sync actions, suppresses all disk writes
```

**Verify:** The unchecked dry-run item no longer appears in `## Backlog`. The checked item appears in `## Done`.

---

## Phase 6 — Commit and push

Run the following commands in order. Do not squash or amend.

```bash
git add ser-sync-pro/src/ser_sync_config.java
git add ser-sync-pro/src/ser_sync_main.java
git add ser-sync-pro/ser-sync.properties.template
git add md/TODO.md
git commit -m "feat(cli): add --dry-run flag to preview sync without writing to disk"
git push origin master
```

**Verify:** `git status` is clean. `git log --oneline -1` shows the commit. Running `ant test` still passes all 26 tests.

---

## Done

All phases complete when:

1. `ser_sync_config.isDryRun()` / `setDryRun()` exist and compile
2. `--dry-run` arg is parsed in `main()` and wired into config
3. All 7 write sites in `runSync()` are guarded with `[DRY RUN]` log output
4. Properties template documents the flag
5. `md/TODO.md` reflects dry-run as complete
6. Commit is pushed and `ant test` is green
