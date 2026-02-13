import java.io.File;
import java.io.IOException;
import java.util.Properties;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

/**
 * Main entry point for serato-sync.
 * Syncs filesystem directory structure to Serato crates.
 */
public class ser_sync_main {

    public static void main(String[] args) {
        // Try to load config to check mode
        ser_sync_config initialConfig;
        try {
            initialConfig = new ser_sync_config();
        } catch (IOException e) {
            // No config file — default to GUI mode
            initialConfig = null;
        }

        boolean guiMode = (initialConfig == null) || initialConfig.isGuiMode();

        if (guiMode) {
            launchGui(initialConfig);
        } else {
            runSync(initialConfig);
        }
    }

    /**
     * GUI mode: Show the config window, wait for Start click, then sync.
     */
    private static void launchGui(ser_sync_config initialConfig) {
        // Use cross-platform L&F so our dark theme colors render correctly
        // (macOS Aqua L&F ignores setBackground on most components)
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            // Fall back to default L&F
        }

        // macOS dark title bar
        System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua");

        javax.swing.SwingUtilities.invokeLater(() -> {
            ser_sync_pro_window window = new ser_sync_pro_window();

            // Install the window as the log handler BEFORE any logging
            ser_sync_log_window_handler handler = new ser_sync_log_window_handler(window);
            ser_sync_log_window_handler.install(handler);
            ser_sync_log.setMode(true);

            // Load saved settings into controls
            if (initialConfig != null) {
                window.loadFromProperties(initialConfig.getProperties());
            }

            // When Start is clicked, run sync on background thread
            window.setOnStartCallback(() -> {
                SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() {
                        Properties guiProps = window.collectProperties();
                        ser_sync_config config = new ser_sync_config(guiProps);
                        runSync(config);
                        return null;
                    }

                    @Override
                    protected void done() {
                        window.syncFinished();
                    }
                };
                worker.execute();
            });
        });
    }

    /**
     * Core sync logic — used by both GUI and CLI modes.
     */
    private static void runSync(ser_sync_config config) {
        // Set mode (GUI vs command line)
        ser_sync_log.setMode(config.isGuiMode());

        // Check Serato library exists
        String seratoPath = config.getSeratoLibraryPath();

        // Set log directory to volume (alongside backup and dupes)
        File seratoDir = new File(seratoPath);
        File volumeLogDir = new File(seratoDir.getParentFile(), "ser-sync-pro/logs");
        ser_sync_log.setLogDirectory(volumeLogDir.getAbsolutePath());

        ser_sync_log.info("ser-sync-pro started");

        // Backup Serato folder
        if (config.isBackupEnabled()) {
            String backupPath = ser_sync_backup.createBackup(seratoPath);
            if (backupPath == null) {
                ser_sync_log.error("Backup failed. Aborting sync for safety.");
                ser_sync_log.fatalError();
                return;
            }
        }

        // Load media library
        ser_sync_log.info("Scanning media library " + config.getMusicLibraryPath() + "...");
        ser_sync_media_library fsLibrary = ser_sync_media_library.readFrom(config.getMusicLibraryPath());
        if (fsLibrary.getTotalNumberOfTracks() <= 0) {
            ser_sync_log.error("Unable to find any supported files in your media library directory.");
            ser_sync_log.error("Are you sure you specified the right path in the config file?");
            ser_sync_log.fatalError();
            return;
        }
        ser_sync_log.info("Found " + fsLibrary.getTotalNumberOfTracks() + " tracks in " +
                fsLibrary.getTotalNumberOfDirectories() + " directories");

        // Move duplicate files BEFORE building crates (so crates don't point to moved
        // files)
        if (config.isHardDriveDupeScanEnabled()) {
            if (config.isDupeMoveEnabled()) {
                java.util.Map<String, String> movedToKept = ser_sync_dupe_mover.scanAndMoveDuplicates(
                        config.getMusicLibraryPath(), fsLibrary, config.getDupeDetectionMode(),
                        config.getDupeMoveMode());
                if (!movedToKept.isEmpty()) {
                    // Update database V2 to point moved paths to kept paths
                    String databasePath = seratoPath + "/database V2";
                    int dbUpdated = ser_sync_database_fixer.updatePaths(databasePath, movedToKept);
                    if (dbUpdated > 0) {
                        ser_sync_log.info("Updated " + dbUpdated + " paths in database V2 for moved duplicates");
                    }

                    // Rescan library to get fresh state after files moved
                    // This ensures broken path fixer can find files at their current locations
                    ser_sync_log.info("Rescanning media library after duplicate removal...");
                    fsLibrary = ser_sync_media_library.readFrom(config.getMusicLibraryPath());
                    ser_sync_log.info("Found " + fsLibrary.getTotalNumberOfTracks() + " tracks remaining.");
                }
            } else {
                // Log-only mode runs after crates are built (doesn't affect anything)
            }
        }

        ser_sync_log.info("Writing files into serato library " + seratoPath + "...");
        if (!new File(seratoPath).isDirectory()) {
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

        // Load Serato database for path normalization
        ser_sync_database database = ser_sync_database.readFrom(seratoPath + "/database V2");
        if (database == null) {
            ser_sync_log.info("No existing Serato database found. Skipping path normalization.");
        }

        // Validate parent crate path
        String parentCratePath = config.getParentCratePath();
        if (parentCratePath != null) {
            ser_sync_log.info("Using parent crate: " + parentCratePath);

            File parentCrateFile = new File(seratoPath + "/Subcrates/" + parentCratePath + ".crate");
            if (!parentCrateFile.exists()) {
                ser_sync_log
                        .info("Parent crate '" + parentCratePath + "' does not exist. Creating it automatically...");
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

            // Check for duplicates
            File subcratesDir = new File(seratoPath + "/Subcrates");
            if (subcratesDir.isDirectory()) {
                int count = 0;
                File[] crateFiles = subcratesDir.listFiles();
                if (crateFiles != null) {
                    for (File f : crateFiles) {
                        if (f.getName().equalsIgnoreCase(parentCratePath + ".crate")) {
                            count++;
                        }
                    }
                }
                if (count > 1) {
                    ser_sync_log.error("Duplicate parent crate detected: found " + count + " crates named '"
                            + parentCratePath + "'.");
                    ser_sync_log.error("Please resolve the duplication in Serato before syncing.");
                    ser_sync_log.fatalError();
                    return;
                }
            }
        }

        // Load track index for deduplication
        ser_sync_track_index trackIndex = null;
        if (config.isSkipExistingTracks()) {
            trackIndex = ser_sync_track_index.createFrom(seratoPath, config.getDedupMode());
        }

        // Build crate library
        ser_sync_library crateLibrary = ser_sync_library.createFrom(fsLibrary, parentCratePath, trackIndex);

        try {
            crateLibrary.writeTo(seratoPath, config.isClearLibraryBeforeSync());
        } catch (ser_sync_exception e) {
            ser_sync_log.error("Error occurred!");
            ser_sync_log.error(e);
            ser_sync_log.fatalError();
            return;
        }

        // Fix broken paths in crates AFTER writing them (so fixes aren't overwritten)
        if (config.isFixBrokenPathsEnabled()) {
            ser_sync_crate_fixer.fixBrokenPaths(seratoPath, fsLibrary, database, config.getDupeMoveMode());
        }

        // Summary
        if (trackIndex != null && trackIndex.getSkippedCount() > 0) {
            ser_sync_log.info("Skipped " + trackIndex.getSkippedCount() + " duplicate tracks");
        }

        // Log-only duplicate scan (if move disabled, we still want to log them)
        if (config.isHardDriveDupeScanEnabled() && !config.isDupeMoveEnabled()) {
            scanForHardDriveDuplicates(fsLibrary);
        }

        ser_sync_log.info("Sync Complete");

        // Sort crates if enabled
        if (config.isCrateSortingEnabled()) {
            ser_sync_pref_sorter.sort(seratoPath);
        }

        ser_sync_log.success();
    }

    private static void scanForHardDriveDuplicates(ser_sync_media_library library) {
        ser_sync_log.info("Scanning for hard drive duplicates...");
        java.util.List<String> allTracks = new java.util.ArrayList<>();
        library.flattenTracks(allTracks);

        java.util.Map<String, java.util.List<String>> groups = new java.util.HashMap<>();
        for (String path : allTracks) {
            java.io.File f = new java.io.File(path);
            String key = f.getName().toLowerCase() + "|" + f.length();
            groups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(path);
        }

        int dupeCount = 0;
        for (java.util.Map.Entry<String, java.util.List<String>> entry : groups.entrySet()) {
            java.util.List<String> paths = entry.getValue();
            if (paths.size() > 1) {
                dupeCount++;
                ser_sync_log.dupe("Duplicate group: " + entry.getKey());
                for (String path : paths) {
                    ser_sync_log.dupe("  " + path);
                }
                ser_sync_log.dupe("");
            }
        }

        if (dupeCount > 0) {
            ser_sync_log.info("Found " + dupeCount
                    + " duplicate file groups on hard drive. See logs/ser-sync-dupe-files-*.log for details.");
        } else {
            ser_sync_log.info("No hard drive duplicates found.");
        }
    }
}
