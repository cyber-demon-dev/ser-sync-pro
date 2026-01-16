import java.io.File;

/**
 * Serato Session Path Fixer
 * 
 * Standalone application that:
 * 1. Backs up the _Serato_ folder (optional)
 * 2. Scans session files for broken file paths
 * 3. Fixes broken paths by looking up files in the music library
 * 
 * Usage: java -jar session-fixer.jar
 * 
 * Configuration: session-fixer.properties
 */
public class session_fixer_main {

    public static void main(String[] args) {
        session_fixer_config config = null;

        try {
            config = new session_fixer_config();
        } catch (Exception e) {
            System.err.println("Error loading configuration: " + e.getMessage());
            System.err.println("Make sure session-fixer.properties exists in the current directory.");
            System.exit(1);
        }

        // Initialize logging with session-fixer log file
        ser_sync_log.setLogFile("session-fixer.log");
        if (config.isGuiMode()) {
            ser_sync_log.setMode(true);
        } else {
            ser_sync_log.setMode(false);
        }

        ser_sync_log.info("=== Serato Session Path Fixer ===");
        ser_sync_log.info("");

        String seratoPath = config.getSeratoPath();
        String[] musicPaths = config.getMusicLibraryPaths();

        // Validate Serato path
        File seratoDir = new File(seratoPath);
        if (!seratoDir.exists()) {
            ser_sync_log.error("Serato folder not found: " + seratoPath);
            ser_sync_log.error("Please check your music.library.serato setting.");
            ser_sync_log.fatalError();
        }

        File sessionsDir = new File(seratoPath + "/History/Sessions");
        if (!sessionsDir.exists()) {
            ser_sync_log.error("History/Sessions folder not found in: " + seratoPath);
            ser_sync_log.error("");
            ser_sync_log.error("The History folder only exists in your HOME _Serato_ folder:");
            ser_sync_log.error("  ~/Music/_serato_  (not on external drives)");
            ser_sync_log.error("");
            ser_sync_log.error("Please update music.library.serato in session-fixer.properties");
            ser_sync_log.fatalError();
        }

        // Validate at least one music path exists
        int validPaths = 0;
        for (String path : musicPaths) {
            if (new File(path).exists()) {
                validPaths++;
            } else {
                ser_sync_log.info("Note: Music path not found (will skip): " + path);
            }
        }
        if (validPaths == 0) {
            ser_sync_log.error("No valid music library paths found.");
            ser_sync_log.error("Please check your music.library.filesystem setting.");
            ser_sync_log.fatalError();
        }

        ser_sync_log.info("Serato folder: " + seratoPath);
        ser_sync_log.info("Music libraries (" + musicPaths.length + " configured):");
        for (int i = 0; i < musicPaths.length; i++) {
            String status = new File(musicPaths[i]).exists() ? "OK" : "NOT FOUND";
            ser_sync_log.info("  " + (i + 1) + ". " + musicPaths[i] + " [" + status + "]");
        }
        ser_sync_log.info("");

        // Step 1: Backup (if enabled)
        if (config.isBackupEnabled()) {
            ser_sync_log.info("Creating backup...");
            String backupPath = ser_sync_backup.createBackup(seratoPath);
            if (backupPath == null) {
                ser_sync_log.error("Backup failed. Aborting to protect your data.");
                ser_sync_log.fatalError();
            }
            ser_sync_log.info("");
        } else {
            ser_sync_log.info("Warning: Backup is disabled. Proceeding without backup.");
            ser_sync_log.info("");
        }

        // Step 2: Scan all music libraries
        ser_sync_log.info("Scanning music libraries for file lookups...");
        java.util.List<ser_sync_media_library> libraries = new java.util.ArrayList<>();
        for (String path : musicPaths) {
            if (new File(path).exists()) {
                ser_sync_log.info("  Scanning: " + path);
                libraries.add(ser_sync_media_library.readFrom(path));
            }
        }
        ser_sync_log.info("");

        // Step 3: Clean up short sessions (before path fixing to save time)
        int minDuration = config.getMinSessionDuration();
        if (minDuration > 0) {
            session_fixer_core_logic.deleteShortSessions(seratoPath, minDuration);
        }

        // Step 4: Fix session paths (checking all libraries in order)
        session_fixer_core_logic.fixBrokenPaths(seratoPath, libraries, null);

        ser_sync_log.info("");
        ser_sync_log.info("=== Session Path Fixer Complete ===");

        if (config.isGuiMode()) {
            ser_sync_log.info("");
            ser_sync_log.info("You may close this window.");
        }
    }
}
