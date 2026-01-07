import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

/**
 * Configuration loader for serato-sync.
 * Reads settings from ser-sync.properties file.
 */
public class ser_sync_config {

    public static final String CONFIG_FILE = "ser-sync.properties";
    public static final String CONFIG_FILE_ALT = "ser-sync.properties.txt";

    private Properties properties;

    public ser_sync_config() throws IOException {
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
        return getRequiredOption("music.library.database");
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
            ser_sync_log.error("Invalid 'crate.parent.path': nested paths are not supported.");
            ser_sync_log.error("Use a single crate name like 'Current', not 'Current%%2025'.");
            ser_sync_log.fatalError();
        }
        return path;
    }

    // ==================== Sync Options ====================

    public boolean isClearLibraryBeforeSync() {
        return getBooleanOption("music.library.database.clear-before-sync", false);
    }

    // ==================== Backup ====================

    public boolean isBackupEnabled() {
        return getBooleanOption("music.library.database.backup", true);
    }

    // ==================== Deduplication ====================

    public boolean isSkipExistingTracks() {
        return getBooleanOption("crate.skip.existing.tracks", true);
    }

    public String getDedupMode() {
        String mode = properties.getProperty("crate.dedupe.mode");
        if (mode == null || mode.trim().isEmpty()) {
            return ser_sync_track_index.MODE_PATH; // default
        }
        return mode.trim().toLowerCase();
    }

    public boolean isHardDriveDupeScanEnabled() {
        return getBooleanOption("harddrive.dupe.scan.enabled", false);
    }

    public boolean isCrateSortingEnabled() {
        return getBooleanOption("crate.sorting.alphabetical", false);
    }

    public boolean isFixBrokenPathsEnabled() {
        return getBooleanOption("crate.fix.broken.paths", false);
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
            ser_sync_log.error("Required config option '" + name + "' is not specified");
            ser_sync_log.fatalError();
        }
        return result;
    }
}
