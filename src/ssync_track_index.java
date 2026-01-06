import java.io.File;

/**
 * Unified track index that combines data from database V2 and crate files.
 * Provides deduplication lookups in path or filename mode.
 */
public class ssync_track_index {

    public static final String MODE_PATH = "path";
    public static final String MODE_FILENAME = "filename";
    public static final String MODE_OFF = "off";

    private ssync_database database;
    private ssync_crate_scanner crateScanner;
    private String mode;
    private int skippedCount = 0;

    /**
     * Creates a track index from the Serato library.
     * 
     * @param seratoPath Path to _Serato_ folder
     * @param mode       Deduplication mode: "path", "filename", or "off"
     * @return ssync_track_index instance
     */
    public static ssync_track_index createFrom(String seratoPath, String mode) {
        ssync_track_index index = new ssync_track_index();
        index.mode = mode != null ? mode.toLowerCase() : MODE_FILENAME;

        if (MODE_OFF.equals(index.mode)) {
            ssync_log.info("Deduplication disabled");
            return index;
        }

        // Load database V2
        String dbPath = seratoPath + "/database V2";
        if (new File(dbPath).exists()) {
            ssync_log.info("Loading database V2 for duplicate detection...");
            index.database = ssync_database.readFrom(dbPath);
            if (index.database != null) {
                ssync_log.info("Found " + index.database.getTrackCount() + " tracks in database V2");
            }
        }

        // Scan crate files
        ssync_log.info("Scanning existing crate files...");
        index.crateScanner = ssync_crate_scanner.scanFrom(seratoPath);
        ssync_log.info("Found " + index.crateScanner.getTrackCount() + " tracks in " +
                index.crateScanner.getCrateCount() + " crate files");

        return index;
    }

    /**
     * Checks if a track already exists in the Serato library.
     * 
     * @param trackPath Full path to the track file
     * @param fileSize  File size string (can be null)
     * @return true if track should be skipped (already exists)
     */
    public boolean shouldSkipTrack(String trackPath, String fileSize) {
        if (MODE_OFF.equals(mode)) {
            return false;
        }

        boolean exists = false;

        if (MODE_FILENAME.equals(mode)) {
            // Filename-based matching
            if (database != null && database.containsTrackByFilename(trackPath, fileSize)) {
                exists = true;
            }
            if (!exists && crateScanner != null && crateScanner.containsTrackByFilename(trackPath)) {
                exists = true;
            }
        } else {
            // Path-based matching (default)
            if (database != null && database.containsTrackByPath(trackPath, fileSize)) {
                exists = true;
            }
            if (!exists && crateScanner != null && crateScanner.containsTrackByPath(trackPath)) {
                exists = true;
            }
        }

        if (exists) {
            // Don't log each skip - just count them
            skippedCount++;
        }

        return exists;
    }

    /**
     * Returns the number of tracks skipped due to deduplication.
     */
    public int getSkippedCount() {
        return skippedCount;
    }

    /**
     * Returns total unique tracks indexed from all sources.
     */
    public int getTotalIndexedTracks() {
        int total = 0;
        if (database != null) {
            total += database.getTrackCount();
        }
        // Note: crate scanner may have overlapping tracks with database
        return total;
    }

    /**
     * Returns the database reference for path encoding lookup.
     */
    public ssync_database getDatabase() {
        return database;
    }
}
