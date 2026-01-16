import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Scans for duplicate files and moves duplicates to a timestamped folder.
 * Supports two modes:
 * - "oldest": Keep newest file, move older duplicates
 * - "newest": Keep oldest file, move newer duplicates
 * 
 * Preserves the original folder structure within the dupes folder.
 * 
 * Output structure:
 * ser-sync-pro/dupes/<timestamp>/
 * ├── dupes.log
 * └── <relative-path>/filename
 */
public class ser_sync_dupe_mover {

    private static final String DUPES_FOLDER = "ser-sync-pro/dupes";
    private static List<String> logEntries = new ArrayList<>();
    private static Map<String, String> movedToKeptMap = new HashMap<>(); // moved path -> kept path
    private static int totalGroupsFound = 0;
    private static int totalFilesMoved = 0;
    private static String currentMoveMode = ser_sync_config.DUPE_MOVE_KEEP_NEWEST;

    /**
     * Scans media library for duplicates and moves copies to dupes folder.
     * 
     * @param musicLibraryRoot Root path of the music library
     * @param library          The scanned media library
     * @param detectionMode    Detection strategy: "name-and-size", "name-only", or
     *                         "off"
     * @param moveMode         Move strategy: "oldest" (keep newest), "newest" (keep
     *                         oldest)
     * @return Map of moved file paths to their kept replacement paths (for database
     *         updates)
     */
    public static Map<String, String> scanAndMoveDuplicates(String musicLibraryRoot,
            ser_sync_media_library library,
            String detectionMode,
            String moveMode) {
        ser_sync_log.info("Duplicate detection mode: " + detectionMode);

        // If detection is off, skip scanning
        if ("off".equals(detectionMode)) {
            ser_sync_log.info("Duplicate detection is disabled.");
            return new HashMap<>();
        }

        ser_sync_log.info("Scanning for duplicates to move...");

        // Reset state
        logEntries.clear();
        movedToKeptMap.clear();
        totalGroupsFound = 0;
        totalFilesMoved = 0;
        currentMoveMode = moveMode;

        // Log move strategy
        if (ser_sync_config.DUPE_MOVE_KEEP_NEWEST.equals(moveMode)) {
            ser_sync_log.info("Move strategy: Keep newest, move older files");
        } else {
            ser_sync_log.info("Move strategy: Keep oldest, move newer files");
        }

        // Flatten all tracks
        List<String> allTracks = new ArrayList<>();
        library.flattenTracks(allTracks);
        ser_sync_log.info("Total tracks scanned: " + allTracks.size());

        // Group by filename + size (or just filename)
        Map<String, List<String>> groups = new HashMap<>();
        for (String path : allTracks) {
            File f = new File(path);
            String key;
            if ("name-only".equals(detectionMode)) {
                key = f.getName().toLowerCase();
            } else if ("name-and-size".equals(detectionMode)) {
                key = f.getName().toLowerCase() + "|" + f.length();
            } else {
                // Invalid mode, default to name-and-size
                ser_sync_log.error("Invalid detection mode '" + detectionMode + "', defaulting to name-and-size");
                key = f.getName().toLowerCase() + "|" + f.length();
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(path);
        }

        ser_sync_log.info("Total unique filename+size combinations: " + groups.size());

        // Find duplicate groups
        List<Map.Entry<String, List<String>>> dupeGroups = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            if (entry.getValue().size() > 1) {
                dupeGroups.add(entry);
                // Debug: log each duplicate group found
                ser_sync_log.info("Duplicate group found: " + entry.getKey() +
                        " (" + entry.getValue().size() + " files)");
                for (String path : entry.getValue()) {
                    ser_sync_log.info("  - " + path);
                }
            }
        }

        if (dupeGroups.isEmpty()) {
            ser_sync_log.info("No duplicates found.");
            return movedToKeptMap;
        }

        totalGroupsFound = dupeGroups.size();
        ser_sync_log.info("Found " + totalGroupsFound + " duplicate groups.");

        // Create timestamped folder
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File libraryRoot = new File(musicLibraryRoot);
        File dupesRoot = new File(libraryRoot.getParentFile(), DUPES_FOLDER + "/" + timestamp);

        // Create dupes folder (parent directories may already exist)
        if (!dupesRoot.exists()) {
            if (!dupesRoot.mkdirs()) {
                ser_sync_log.error("Failed to create dupes folder: " + dupesRoot.getAbsolutePath());
                return movedToKeptMap;
            }
        } else {
            // This should never happen with timestamped folders, but check anyway
            ser_sync_log.error("Dupes folder already exists: " + dupesRoot.getAbsolutePath());
            ser_sync_log.error("This should not happen with timestamped folders. Aborting.");
            return movedToKeptMap;
        }

        // Process each duplicate group
        for (Map.Entry<String, List<String>> entry : dupeGroups) {
            processDuplicateGroup(entry.getKey(), entry.getValue(), musicLibraryRoot, dupesRoot);
        }

        // Write log file with header at top
        File logFile = new File(dupesRoot, "dupes.log");
        writeLogFile(logFile, timestamp);

        ser_sync_log.info("Moved " + totalFilesMoved + " duplicate files to: " + dupesRoot.getAbsolutePath());
        ser_sync_log.info("See " + logFile.getAbsolutePath() + " for details.");

        return movedToKeptMap;
    }

    /**
     * Processes a single duplicate group - keeps one file based on moveMode.
     * - "oldest": Keep newest, move older files
     * - "newest": Keep oldest, move newer files
     */
    private static void processDuplicateGroup(String groupKey, List<String> paths,
            String libraryRoot, File dupesRoot) {

        // Sort by modification time based on move mode
        boolean keepNewest = ser_sync_config.DUPE_MOVE_KEEP_NEWEST.equals(currentMoveMode);
        paths.sort((a, b) -> {
            long timeA = new File(a).lastModified();
            long timeB = new File(b).lastModified();
            if (keepNewest) {
                return Long.compare(timeB, timeA); // Descending (newest first = keep newest)
            } else {
                return Long.compare(timeA, timeB); // Ascending (oldest first = keep oldest)
            }
        });

        String keptPath = paths.get(0);
        File keptFile = new File(keptPath);
        String keptDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date(keptFile.lastModified()));

        logEntries.add("Duplicate group: " + groupKey);
        logEntries.add("  KEPT:  " + keptPath + " (" + keptDate + ")");

        // Move all other files (not the kept one)
        for (int i = 1; i < paths.size(); i++) {
            String movePath = paths.get(i);
            File moveFile = new File(movePath);
            String moveDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date(moveFile.lastModified()));

            // Calculate relative path from library root
            String relativePath = getRelativePath(movePath, libraryRoot);
            File destFile = new File(dupesRoot, relativePath);

            // Create parent directories
            destFile.getParentFile().mkdirs();

            // Move the file
            try {
                Files.move(moveFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logEntries.add("  MOVED: " + movePath + " (" + moveDate + ")");
                logEntries.add("      -> " + destFile.getAbsolutePath());
                movedToKeptMap.put(movePath, keptPath); // Track: moved file -> kept file
                totalFilesMoved++;
            } catch (IOException e) {
                logEntries.add("  ERROR: Failed to move " + movePath + ": " + e.getMessage());
                ser_sync_log.error("Failed to move file: " + movePath + " - " + e.getMessage());
            }
        }

        logEntries.add("");
    }

    /**
     * Gets the relative path from library root.
     */
    private static String getRelativePath(String filePath, String libraryRoot) {
        // Normalize paths
        String normalizedFile = filePath.replace("\\", "/");
        String normalizedRoot = libraryRoot.replace("\\", "/");

        if (!normalizedRoot.endsWith("/")) {
            normalizedRoot += "/";
        }

        if (normalizedFile.startsWith(normalizedRoot)) {
            return normalizedFile.substring(normalizedRoot.length());
        }

        // If not relative, use just the filename
        return new File(filePath).getName();
    }

    /**
     * Writes the complete log file with header at top.
     */
    private static void writeLogFile(File logFile, String timestamp) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile))) {
            // Write header first
            writer.println("=== Duplicate File Scan Report ===");
            writer.println("Date: " + timestamp.replace("_", " "));
            writer.println("Total duplicate groups found: " + totalGroupsFound);
            writer.println("Total files moved: " + totalFilesMoved);
            writer.println("=====================================");
            writer.println();

            // Write all log entries
            for (String entry : logEntries) {
                writer.println(entry);
            }
        } catch (IOException e) {
            ser_sync_log.error("Failed to write dupes log: " + e.getMessage());
        }
    }
}
