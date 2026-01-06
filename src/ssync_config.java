import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

/**
 * Configuration loader for serato-sync.
 * Reads settings from ssync.properties file.
 */
public class ssync_config {

    public static final String CONFIG_FILE = "ssync.properties";
    public static final String CONFIG_FILE_ALT = "ssync.properties.txt";

    private Properties properties;

    public ssync_config() throws IOException {
        properties = new Properties();

        FileInputStream in = null;
        try {
            in = new FileInputStream(CONFIG_FILE);
        } catch (FileNotFoundException e) {
            in = new FileInputStream(CONFIG_FILE_ALT);
        }
        properties.load(in);
        in.close();
    }

    // ==================== Mode ====================

    public boolean isGuiMode() {
        return !("cmd".equals(properties.getProperty("mode")));
    }

    // ==================== Paths ====================

    public String getMusicLibraryPath() {
        return getRequiredOption("music.library.filesystem");
    }

    public String getSeratoLibraryPath() {
        return getRequiredOption("music.library.ssync");
    }

    // ==================== Parent Crate ====================

    public String getParentCratePath() {
        String path = properties.getProperty("crate.parent.path");
        if (path == null || path.trim().length() <= 0) {
            return null;
        }
        path = path.trim();
        // Reject nested paths
        if (path.contains("%%")) {
            ssync_log.error("Invalid 'crate.parent.path': nested paths are not supported.");
            ssync_log.error("Use a single crate name like 'Current', not 'Current%%2025'.");
            ssync_log.fatalError();
        }
        return path;
    }

    // ==================== Sync Options ====================

    public boolean isClearLibraryBeforeSync() {
        return getBooleanOption("music.library.ssync.clear-before-sync", false);
    }

    // ==================== Backup ====================

    public boolean isBackupEnabled() {
        return getBooleanOption("backup.enabled", true);
    }

    // ==================== Deduplication ====================

    public boolean isSkipExistingTracks() {
        return getBooleanOption("skip.existing.tracks", true);
    }

    public String getDedupMode() {
        String mode = properties.getProperty("dedup.mode");
        if (mode == null || mode.trim().isEmpty()) {
            return ssync_track_index.MODE_PATH; // default
        }
        return mode.trim().toLowerCase();
    }

    public boolean isHardDriveDupeScanEnabled() {
        return getBooleanOption("harddrive.dupe.scan.enabled", false);
    }

    // ==================== Helper Methods ====================

    private boolean getBooleanOption(String name, boolean defaultValue) {
        String value = properties.getProperty(name);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.trim());
    }

    private String getRequiredOption(String name) {
        String result = properties.getProperty(name);
        if (result == null || result.trim().length() <= 0) {
            ssync_log.error("Required config option '" + name + "' is not specified");
            ssync_log.fatalError();
        }
        return result;
    }
}
