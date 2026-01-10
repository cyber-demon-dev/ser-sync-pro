import java.io.File;
import java.io.FilenameFilter;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * scanning existing .crate files and fixing broken filepaths
 * by looking up files in the media library by filename.
 */
public class ser_sync_crate_fixer {

    // Thread pool for parallel crate processing
    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();
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
            // Normalize filename to NFC for consistent matching
            String filename = Normalizer.normalize(f.getName().toLowerCase(), Normalizer.Form.NFC);
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

        // Process crates in parallel with progress tracking
        List<Future<?>> futures = new ArrayList<>();
        final String finalVolumeRoot = volumeRoot;
        final int totalCrates = crateFiles.length;
        final AtomicInteger processedCount = new AtomicInteger(0);

        for (File crateFile : crateFiles) {
            futures.add(CRATE_POOL.submit(() -> {
                processCrateFile(crateFile, libraryFiles, database, finalVolumeRoot, pathFixes, crateUpdates);
                int done = processedCount.incrementAndGet();
                ser_sync_log.progress("Checking crates for broken paths", done, totalCrates);
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

        ser_sync_log.progressComplete("Checking crates");

        // 4. Update database V2 file with all path fixes
        if (!pathFixes.isEmpty()) {
            ser_sync_log.info("Updating database V2 with " + pathFixes.size() + " path fixes...");
            String databasePath = seratoPath + "/database V2";
            int dbUpdated = ser_sync_database_fixer.updatePaths(databasePath, pathFixes);
            if (dbUpdated > 0) {
                ser_sync_log.info("Updated " + dbUpdated + " paths in database V2");
            } else {
                ser_sync_log.info("No database paths were updated (paths not found in database)");
            }
        } else {
            ser_sync_log.info("No broken paths need fixing.");
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
                // Normalize to NFC for matching (database may use NFD for accented chars)
                String filename = Normalizer.normalize(trackFile.getName().toLowerCase(), Normalizer.Form.NFC);
                String fixedPath = libraryFiles.get(filename);

                if (fixedPath != null && new File(fixedPath).exists()) {
                    String normalizedPath = fixedPath;

                    // Try to preserve Serato's original filename encoding
                    // Only update the directory path, keep filename exactly as stored in database
                    if (database != null) {
                        String originalFilename = database.getSeratoFilename(fixedPath);
                        if (originalFilename != null) {
                            // Get parent directory from the new filesystem location
                            String newDir = new File(fixedPath).getParent();
                            if (newDir != null) {
                                // Strip volume root to make relative path
                                if (volumeRoot != null && newDir.startsWith(volumeRoot)) {
                                    newDir = newDir.substring(volumeRoot.length());
                                    if (newDir.startsWith("/")) {
                                        newDir = newDir.substring(1);
                                    }
                                }
                                // Combine new directory with original filename encoding
                                normalizedPath = newDir + "/" + originalFilename;
                            }
                        }
                    }

                    if (!trackPath.equals(normalizedPath)) {
                        pathFixes.put(trackPath, normalizedPath);
                    }

                    // Don't log individual fixes - too many from parallel threads causes GUI
                    // slowdown
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
