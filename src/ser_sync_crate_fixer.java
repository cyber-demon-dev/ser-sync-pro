import java.io.File;
import java.io.FilenameFilter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.*;

/**
 * Scanning existing .crate files and fixing broken filepaths
 * by looking up files in the media library by filename.
 * Uses parallel processing for faster crate scanning.
 */
public class ser_sync_crate_fixer {

    // Thread pool for parallel crate processing
    private static final int NUM_THREADS = Math.min(4, Runtime.getRuntime().availableProcessors());
    private static final ExecutorService CRATE_POOL = Executors.newFixedThreadPool(NUM_THREADS);

    /**
     * Fixes broken paths in all .crate files in the given Serato directory.
     * Updates the database V2 file first to prevent duplicates, then updates
     * crates.
     * 
     * @param seratoPath Path to the _Serato_ folder
     * @param library    The scanned media library to use for lookups
     * @param database   The Serato database for path normalization (may be null)
     */
    public static void fixBrokenPaths(String seratoPath, ser_sync_media_library library, ser_sync_database database) {
        ser_sync_log.info("Checking for broken filepaths in crates...");

        // Determine volume root from seratoPath (e.g. /Volumes/Name/_Serato_ ->
        // /Volumes/Name/)
        // If seratoPath is not in _Serato_, assume absolute paths or CWD.
        File seratoDir = new File(seratoPath);
        String volumeRoot = null;
        if (seratoDir.getName().equalsIgnoreCase("_Serato_")) {
            volumeRoot = seratoDir.getParent();
        }

        // 1. Build a map of filename -> absolute path from the media library
        Map<String, String> libraryFiles = new HashMap<>();
        List<String> allTracks = new ArrayList<>();
        library.flattenTracks(allTracks);

        for (String path : allTracks) {
            File f = new File(path);
            String filename = f.getName();
            libraryFiles.put(filename, path);
        }

        // 2. Scan for .crate files
        File subcratesDir = new File(seratoPath + "/Subcrates");
        if (!subcratesDir.exists() || !subcratesDir.isDirectory()) {
            return;
        }

        File[] crateFiles = subcratesDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".crate");
            }
        });

        if (crateFiles == null) {
            return;
        }

        // 3. First pass: collect all path fixes needed (using thread-safe collections)
        Map<String, String> pathFixes = new ConcurrentHashMap<>(); // old path -> new path
        Map<File, List<String>> crateUpdates = new ConcurrentHashMap<>(); // crate file -> new tracks list

        // Process crates in parallel
        List<Future<?>> futures = new ArrayList<>();
        final String finalVolumeRoot = volumeRoot;

        for (File crateFile : crateFiles) {
            futures.add(CRATE_POOL.submit(() -> {
                processCrateFile(crateFile, libraryFiles, database, finalVolumeRoot, pathFixes, crateUpdates);
            }));
        }

        // Wait for all crate processing to complete
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                ser_sync_log.error("Error processing crate: " + e.getMessage());
            }
        }

        // 4. Update database V2 file with all path fixes
        if (!pathFixes.isEmpty()) {
            String databasePath = seratoPath + "/database V2";
            int dbUpdated = ser_sync_database_fixer.updatePaths(databasePath, pathFixes);
            if (dbUpdated > 0) {
                ser_sync_log.info("Updated " + dbUpdated + " paths in database V2");
            }
        }

        // 5. Now update all crate files
        int totalFixedTracks = 0;
        int totalFixedCrates = 0;

        for (Map.Entry<File, List<String>> entry : crateUpdates.entrySet()) {
            File crateFile = entry.getKey();
            List<String> newTracks = entry.getValue();

            ser_sync_crate originalCrate;
            try {
                originalCrate = ser_sync_crate.readFrom(crateFile);
            } catch (ser_sync_exception e) {
                continue;
            }

            ser_sync_crate newCrate = new ser_sync_crate();
            newCrate.setVersion(originalCrate.getVersion());
            newCrate.setSorting(originalCrate.getSorting());
            newCrate.setSortingRev(originalCrate.getSortingRev());
            for (String col : originalCrate.getColumns()) {
                newCrate.addColumn(col);
            }
            newCrate.addTracks(newTracks);

            try {
                newCrate.writeTo(crateFile);
                totalFixedCrates++;
                totalFixedTracks += newTracks.size();
            } catch (ser_sync_exception e) {
                ser_sync_log.error("Failed to write fixed crate: " + crateFile.getName());
            }
        }

        if (totalFixedCrates > 0) {
            ser_sync_log.info("Fixed " + totalFixedCrates + " crate files.");
        } else {
            ser_sync_log.info("No broken paths found that could be fixed.");
        }
    }

    /**
     * Processes a single crate file to find and fix broken paths.
     * Thread-safe - can be called from multiple threads.
     */
    private static void processCrateFile(File crateFile, Map<String, String> libraryFiles,
            ser_sync_database database, String volumeRoot,
            Map<String, String> pathFixes, Map<File, List<String>> crateUpdates) {

        ser_sync_crate crate;
        try {
            crate = ser_sync_crate.readFrom(crateFile);
        } catch (ser_sync_exception e) {
            ser_sync_log.error("Failed to read crate: " + crateFile.getName());
            return;
        }

        List<String> originalTracks = new ArrayList<>(crate.getTracks());
        List<String> newTracks = new ArrayList<>();
        boolean tracksChanged = false;

        for (String trackPath : originalTracks) {
            File trackFile = new File(trackPath);
            boolean exists = trackFile.exists();

            String resolvedPath = trackPath;
            if (!exists && !trackFile.isAbsolute() && volumeRoot != null) {
                File resolvedFile = new File(volumeRoot, trackPath);
                if (resolvedFile.exists()) {
                    exists = true;
                    resolvedPath = resolvedFile.getAbsolutePath();
                }
            }

            if (!exists) {
                String filename = trackFile.getName();
                String fixedPath = libraryFiles.get(filename);

                if (fixedPath != null && new File(fixedPath).exists()) {
                    String normalizedPath = fixedPath;
                    if (database != null) {
                        String dbPath = database.getOriginalPathByFilename(fixedPath);
                        if (dbPath != null && new File(dbPath).exists()) {
                            normalizedPath = dbPath;
                        } else if (dbPath != null) {
                            pathFixes.put(dbPath, fixedPath);
                            normalizedPath = fixedPath;
                        }
                    }

                    if (!trackPath.equals(normalizedPath)) {
                        pathFixes.put(trackPath, normalizedPath);
                    }

                    ser_sync_log.info("Fixed broken path in '" + crateFile.getName() + "':");
                    ser_sync_log.info("  Broken: " + trackPath);
                    ser_sync_log.info("   Found: " + normalizedPath);
                    newTracks.add(normalizedPath);
                    tracksChanged = true;
                } else {
                    newTracks.add(trackPath);
                }
            } else {
                newTracks.add(resolvedPath);
                if (!trackPath.equals(resolvedPath)) {
                    tracksChanged = true;
                }
            }
        }

        if (tracksChanged) {
            crateUpdates.put(crateFile, newTracks);
        }
    }
}
