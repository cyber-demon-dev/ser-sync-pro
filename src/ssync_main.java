import java.io.File;
import java.io.IOException;

/**
 * Main entry point for serato-sync.
 * Syncs filesystem directory structure to Serato crates.
 */
public class ssync_main {

    public static void main(String[] args) {
        // Load configuration
        ssync_config config;
        try {
            config = new ssync_config();
        } catch (IOException e) {
            ssync_log.error("Unable to load config file '" + ssync_config.CONFIG_FILE + "'");
            ssync_log.fatalError();
            return;
        }

        // Set mode (GUI vs command line)
        ssync_log.setMode(config.isGuiMode());

        // Load media library
        ssync_log.info("Scanning media library " + config.getMusicLibraryPath() + "...");
        ssync_media_library fsLibrary = ssync_media_library.readFrom(config.getMusicLibraryPath());
        if (fsLibrary.getTotalNumberOfTracks() <= 0) {
            ssync_log.error("Unable to find any supported files in your media library directory.");
            ssync_log.error("Are you sure you specified the right path in the config file?");
            ssync_log.fatalError();
            return;
        }
        ssync_log.info("Found " + fsLibrary.getTotalNumberOfTracks() + " tracks in " +
                fsLibrary.getTotalNumberOfDirectories() + " directories");

        // Check Serato library exists
        String seratoPath = config.getSeratoLibraryPath();
        ssync_log.info("Writing files into serato library " + seratoPath + "...");
        if (!new File(seratoPath).isDirectory()) {
            ssync_log.error("Unable to detect your Serato library. It doesn't exist.");
            ssync_log.error("Are you sure you specified the right path in the config file?");
            ssync_log.fatalError();
            return;
        }

        // Backup Serato folder
        if (config.isBackupEnabled()) {
            String backupPath = ssync_backup.createBackup(seratoPath);
            if (backupPath == null) {
                ssync_log.error("Backup failed. Aborting sync for safety.");
                ssync_log.fatalError();
                return;
            }
        }

        // Validate parent crate path
        String parentCratePath = config.getParentCratePath();
        if (parentCratePath != null) {
            ssync_log.info("Using parent crate: " + parentCratePath);

            File parentCrateFile = new File(seratoPath + "/Subcrates/" + parentCratePath + ".crate");
            if (!parentCrateFile.exists()) {
                ssync_log.error("Parent crate '" + parentCratePath + "' does not exist in Serato.");
                ssync_log.error("Please create the parent crate in Serato first, then re-run sync.");
                ssync_log.fatalError();
                return;
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
                    ssync_log.error("Duplicate parent crate detected: found " + count + " crates named '"
                            + parentCratePath + "'.");
                    ssync_log.error("Please resolve the duplication in Serato before syncing.");
                    ssync_log.fatalError();
                    return;
                }
            }
        }

        // Load track index for deduplication
        ssync_track_index trackIndex = null;
        if (config.isSkipExistingTracks()) {
            trackIndex = ssync_track_index.createFrom(seratoPath, config.getDedupMode());
        }

        // Build crate library
        ssync_library crateLibrary = ssync_library.createFrom(fsLibrary, parentCratePath, trackIndex);

        try {
            crateLibrary.writeTo(seratoPath, config.isClearLibraryBeforeSync());
        } catch (ssync_exception e) {
            ssync_log.error("Error occurred!");
            ssync_log.error(e);
            ssync_log.fatalError();
            return;
        }

        // Summary
        ssync_log.info("Wrote " + crateLibrary.getTotalNumberOfCrates() + " crates and " +
                crateLibrary.getTotalNumberOfSubCrates() + " subcrates");
        if (trackIndex != null && trackIndex.getSkippedCount() > 0) {
            ssync_log.info("Skipped " + trackIndex.getSkippedCount() + " duplicate tracks");
        }
        ssync_log.info("Enjoy!");

        ssync_log.success();
    }
}
