import java.io.File;
import java.io.FilenameFilter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * scanning existing .crate files and fixing broken filepaths
 * by looking up files in the media library by filename.
 */
public class ser_sync_crate_fixer {

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

        // 3. First pass: collect all path fixes needed
        Map<String, String> pathFixes = new HashMap<>(); // old path -> new path
        Map<File, List<String>> crateUpdates = new HashMap<>(); // crate file -> new tracks list

        for (File crateFile : crateFiles) {
            ser_sync_crate crate;

            try {
                crate = ser_sync_crate.readFrom(crateFile);
            } catch (ser_sync_exception e) {
                ser_sync_log.error("Failed to read crate: " + crateFile.getName());
                continue;
            }

            List<String> originalTracks = new ArrayList<>(crate.getTracks());
            List<String> newTracks = new ArrayList<>();
            boolean tracksChanged = false;

            for (String trackPath : originalTracks) {
                // Check if file exists
                File trackFile = new File(trackPath);
                boolean exists = trackFile.exists();

                // If path is relative and we know the volume root, try resolving it
                String resolvedPath = trackPath;
                if (!exists && !trackFile.isAbsolute() && volumeRoot != null) {
                    File resolvedFile = new File(volumeRoot, trackPath);
                    if (resolvedFile.exists()) {
                        exists = true;
                        resolvedPath = resolvedFile.getAbsolutePath();
                    }
                }

                if (!exists) {
                    // File is missing, try to find it in our library map
                    String filename = trackFile.getName();
                    String fixedPath = libraryFiles.get(filename);

                    if (fixedPath != null && new File(fixedPath).exists()) {
                        // Check if database has an existing path for this file
                        String normalizedPath = fixedPath;
                        if (database != null) {
                            String dbPath = database.getOriginalPathByFilename(fixedPath);
                            if (dbPath != null && new File(dbPath).exists()) {
                                // Database path exists, use it
                                normalizedPath = dbPath;
                            } else if (dbPath != null) {
                                // Database has old path but file moved - we need to update database
                                pathFixes.put(dbPath, fixedPath);
                                normalizedPath = fixedPath;
                            }
                        }

                        // Also track the original broken path for database update
                        if (!trackPath.equals(normalizedPath)) {
                            pathFixes.put(trackPath, normalizedPath);
                        }

                        ser_sync_log.info("Fixed broken path in '" + crateFile.getName() + "':");
                        ser_sync_log.info("  Broken: " + trackPath);
                        ser_sync_log.info("   Found: " + normalizedPath);
                        newTracks.add(normalizedPath);
                        tracksChanged = true;
                    } else {
                        // Still broken or not found, keep original
                        newTracks.add(trackPath);
                    }
                } else {
                    // File exists, keep it (use resolved path if different)
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
}
