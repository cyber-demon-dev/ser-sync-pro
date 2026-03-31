import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixes broken filepaths in existing Serato crates and database V2.
 *
 * Three independent, sequential steps:
 *   1. fixExistingCrates()   — reads .crate files, updates broken paths, writes
 *                              fixed crates. No database involvement.
 *   2. (caller writes new crates via cdd_sync_library)
 *   3. updateDatabasePaths() — reads database V2, updates broken pfil paths. Runs last.
 */
public class cdd_sync_crate_fixer {

    // =========================================================================
    // Step 2 — Fix existing .crate files (database V2 as source of truth)
    // =========================================================================
    /**
     * Scans every .crate file in _Serato_/Subcrates and updates track paths
     * using the Serato database V2 as the sole source of truth.
     *
     * For each track in each crate: look up its filename in the database.
     * If the database holds a different path for that filename, replace the
     * crate's stored path with the database path. Crates that had no changes
     * are left untouched.
     *
     * This approach covers ALL crates — including hand-curated Live sets with
     * no corresponding filesystem directory — because it relies on the database
     * (already updated by Step 1) rather than filesystem existence checks.
     *
     * Uses setTracksRaw() so dedup never removes a valid track.
     *
     * @param seratoPath Path to the _Serato_ folder
     * @param database   Database V2 freshly reloaded after Step 1 updated it
     */
    public static void fixExistingCrates(String seratoPath, cdd_sync_database database) {
        cdd_sync_log.info("Step 2: Rewriting crate paths from database V2...");

        if (database == null) {
            cdd_sync_log.info("No database V2 — skipping Step 2.");
            return;
        }

        // Build a flat filename -> relative-path map from the authoritative database.
        // The database is the source of truth after Step 1 patched it.
        Map<String, String> dbPathByFilename = new HashMap<>();
        for (String dbPath : database.getAllTrackPaths()) {
            String filename = cdd_sync_binary_utils.getFilename(dbPath);
            dbPathByFilename.put(filename, dbPath);
        }

        if (dbPathByFilename.isEmpty()) {
            cdd_sync_log.info("Database is empty — skipping Step 2.");
            return;
        }

        File subcratesDir = new File(seratoPath + "/Subcrates");
        if (!subcratesDir.isDirectory()) {
            cdd_sync_log.info("No Subcrates directory — skipping Step 2.");
            return;
        }

        // ALL crates — folder-mapped and custom hand-curated alike.
        File[] crateFiles = subcratesDir.listFiles((d, name) -> name.endsWith(".crate"));
        if (crateFiles == null || crateFiles.length == 0) {
            cdd_sync_log.info("No crate files found.");
            return;
        }

        int fixedCrates = 0;
        int fixedPaths  = 0;

        for (File crateFile : crateFiles) {
            cdd_sync_crate crate;
            try {
                crate = cdd_sync_crate.readFrom(crateFile);
            } catch (cdd_sync_exception e) {
                cdd_sync_log.error("Failed to read crate: " + crateFile.getName());
                continue;
            }

            List<String> original = new ArrayList<>(crate.getTracks());
            List<String> updated  = new ArrayList<>(original.size());
            boolean changed = false;

            for (String trackPath : original) {
                String filename = cdd_sync_binary_utils.getFilename(trackPath);
                String dbPath   = dbPathByFilename.get(filename);

                if (dbPath != null && !dbPath.equals(trackPath)) {
                    cdd_sync_log.step2("[PATH FIX] " + crateFile.getName()
                            + " | OLD: " + trackPath
                            + " -> NEW: " + dbPath);
                    updated.add(dbPath);
                    changed = true;
                    fixedPaths++;
                } else {
                    updated.add(trackPath);
                }
            }

            if (changed) {
                cdd_sync_crate fixedCrate = new cdd_sync_crate();
                fixedCrate.setVersion(crate.getVersion());
                fixedCrate.setSorting(crate.getSorting());
                fixedCrate.setSortingRev(crate.getSortingRev());
                for (String col : crate.getColumns()) {
                    fixedCrate.addColumn(col);
                }
                fixedCrate.setTracksRaw(updated);
                try {
                    fixedCrate.writeTo(crateFile);
                    fixedCrates++;
                    cdd_sync_log.fix("[CRATE FIXED] " + crateFile.getName()
                            + " (" + fixedCrate.getTrackCount() + " tracks)");
                } catch (cdd_sync_exception e) {
                    cdd_sync_log.error("Failed to write crate: " + crateFile.getName());
                }
            }
        }

        cdd_sync_log.info("Step 2 complete: " + fixedPaths + " paths fixed across "
                + fixedCrates + " crates.");
    }

    // =========================================================================
    // Step 1 — Update database V2 paths (runs before crate fixes)
    // =========================================================================

    /**
     * Reads the Serato database V2, checks each stored track path against the
     * filesystem, and updates any broken paths by filename lookup.
     * This runs LAST — after fixExistingCrates() and new crate writing are done.
     *
     * @param seratoPath Path to the _Serato_ folder
     * @param library    Scanned media library for filename lookups
     */
    public static void updateDatabasePaths(String seratoPath, cdd_sync_media_library library) {
        cdd_sync_log.info("Step 1: Updating broken paths in database V2...");

        String databasePath = seratoPath + "/database V2";
        cdd_sync_database database = cdd_sync_database.readFrom(databasePath);
        if (database == null) {
            cdd_sync_log.error("No Serato database V2 found at: " + seratoPath);
            return;
        }

        String volumeRoot = getVolumeRoot(seratoPath);
        Map<String, List<String>> libraryFiles = buildLibraryIndex(library);

        // Walk every path in the database, find broken ones, collect fixes.
        // Use LinkedHashMap to preserve insertion order for deterministic output.
        Map<String, String> pathFixes = new LinkedHashMap<>();

        for (String dbTrackPath : database.getAllTrackPaths()) {
            String absolutePath = volumeRoot != null
                    ? volumeRoot + "/" + dbTrackPath
                    : dbTrackPath;

            if (new File(absolutePath).exists()) {
                continue; // Still valid — skip.
            }

            String filename = cdd_sync_binary_utils.getFilename(dbTrackPath);
            List<String> candidates = libraryFiles.get(filename);

            if (candidates == null || candidates.isEmpty()) {
                // Can't resolve — leave the database entry as-is.
                continue;
            }
            if (candidates.size() > 1) {
                cdd_sync_log.step1("[DB AMBIGUOUS] " + candidates.size()
                        + " candidates for: " + filename);
                continue;
            }

            String fixedRelative = cdd_sync_binary_utils
                    .normalizePathForDatabase(candidates.get(0));

            cdd_sync_log.step1("[DB FIX] BROKEN: " + dbTrackPath
                    + " -> FIXED: " + fixedRelative);

            pathFixes.put(dbTrackPath, fixedRelative);
        }

        if (pathFixes.isEmpty()) {
            cdd_sync_log.info("No broken paths found in database V2.");
            return;
        }

        cdd_sync_log.info("Updating database V2 with " + pathFixes.size() + " path fixes...");
        int updated = cdd_sync_database_fixer.updatePaths(databasePath, pathFixes);
        cdd_sync_log.info("Updated " + updated + " paths in database V2.");
    }

    // =========================================================================
    // Backward-compatible facade (used by runFixPaths and any legacy callers)
    // =========================================================================

    /**
     * Facade over the two independent fix steps (steps 1 & 2 only).
     * Kept for backward compatibility with runFixPaths().
     */
    public static void fixBrokenPaths(String seratoPath, cdd_sync_media_library library,
            cdd_sync_database database, String dupeMoveMode) {
        updateDatabasePaths(seratoPath, library);
        // Reload DB after Step 1 so Step 2 sees the updated paths.
        cdd_sync_database updatedDatabase = cdd_sync_database.readFrom(
                seratoPath + "/database V2");
        fixExistingCrates(seratoPath, updatedDatabase);
    }

    // =========================================================================
    // Step 3 — Append new tracks to existing crates matching library filepath
    // =========================================================================

    /**
     * For each folder-path crate that ALREADY EXISTS on disk, appends any new
     * tracks from the corresponding media library folder. Uses addTrack()
     * (filename-based dedup) so tracks already present are never duplicated.
     *
     * @param seratoPath      Path to the _Serato_ folder
     * @param library         Scanned media library
     * @param parentCratePath Crate prefix (e.g. "Current"), or null for none
     * @param trackIndex      Track index with database reference (may be null)
     */
    public static void appendNewTracksToMatchingCrates(String seratoPath,
            cdd_sync_media_library library, String parentCratePath,
            cdd_sync_track_index trackIndex) {
        cdd_sync_log.info("Step 3: Appending new tracks to existing crates...");

        File subcratesDir = new File(seratoPath + "/Subcrates");
        if (!subcratesDir.exists()) {
            cdd_sync_log.info("No Subcrates directory found, skipping append step.");
            return;
        }

        Map<String, List<String>> crateMap = buildCrateFileMap(library, parentCratePath);
        cdd_sync_database database = (trackIndex != null) ? trackIndex.getDatabase() : null;

        int appendedCount = 0;
        int skippedCount = 0;

        for (Map.Entry<String, List<String>> entry : crateMap.entrySet()) {
            String crateFileName = entry.getKey();
            List<String> newTracks = entry.getValue();
            File crateFile = new File(subcratesDir, crateFileName);

            if (!crateFile.exists()) {
                continue; // Step 4 will create it.
            }

            cdd_sync_crate crate;
            try {
                crate = cdd_sync_crate.readFrom(crateFile);
            } catch (cdd_sync_exception e) {
                cdd_sync_log.error("Failed to read crate for append: " + crateFileName);
                continue;
            }

            int before = crate.getTrackCount();
            if (database != null) {
                crate.setDatabase(database);
            }
            for (String track : newTracks) {
                crate.addTrack(track);
            }
            int after = crate.getTrackCount();

            if (after > before) {
                try {
                    crate.writeTo(crateFile);
                    cdd_sync_log.step3("[CRATE APPENDED] " + crateFileName
                            + " (+" + (after - before) + " tracks, total " + after + ")");
                    appendedCount++;
                } catch (cdd_sync_exception e) {
                    cdd_sync_log.error("Failed to write appended crate: " + crateFileName);
                }
            } else {
                cdd_sync_log.step3("[CRATE APPEND SKIPPED] " + crateFileName
                        + " (" + before + " tracks, no new tracks)");
                skippedCount++;
            }
        }

        if (appendedCount > 0) {
            cdd_sync_log.info("Step 3 complete: " + appendedCount + " crates updated.");
        } else {
            cdd_sync_log.info("Step 3 complete: no new tracks to append ("
                    + skippedCount + " unchanged).");
        }
    }

    // =========================================================================
    // Step 4 — Create new crates for new library paths (never overwrite)
    // =========================================================================

    /**
     * For each folder-path crate that does NOT yet exist on disk, creates and
     * writes a new .crate file populated from that folder. Existing crates are
     * always skipped — Step 3 handles them.
     *
     * @param seratoPath      Path to the _Serato_ folder
     * @param library         Scanned media library
     * @param parentCratePath Crate prefix (e.g. "Current"), or null for none
     * @param trackIndex      Track index with database reference (may be null)
     */
    public static void createNewCrates(String seratoPath,
            cdd_sync_media_library library, String parentCratePath,
            cdd_sync_track_index trackIndex) {
        cdd_sync_log.info("Step 4: Creating new crates for new library paths...");

        File subcratesDir = new File(seratoPath + "/Subcrates");
        Map<String, List<String>> crateMap = buildCrateFileMap(library, parentCratePath);
        cdd_sync_database database = (trackIndex != null) ? trackIndex.getDatabase() : null;

        int createdCount = 0;
        int skippedCount = 0;

        for (Map.Entry<String, List<String>> entry : crateMap.entrySet()) {
            String crateFileName = entry.getKey();
            List<String> tracks = entry.getValue();
            File crateFile = new File(subcratesDir, crateFileName);

            if (crateFile.exists()) {
                cdd_sync_log.step4("[CRATE EXISTS, SKIPPED] " + crateFileName);
                skippedCount++;
                continue;
            }

            cdd_sync_crate crate = new cdd_sync_crate();
            if (database != null) {
                crate.setDatabase(database);
            }
            crate.addTracks(tracks);

            try {
                crateFile.getParentFile().mkdirs();
                crate.writeTo(crateFile);
                cdd_sync_log.step4("[CRATE CREATED] " + crateFileName
                        + " (" + crate.getTrackCount() + " tracks)");
                createdCount++;
            } catch (cdd_sync_exception e) {
                cdd_sync_log.error("Failed to write new crate: " + crateFileName);
            }
        }

        if (createdCount > 0) {
            cdd_sync_log.info("Step 4 complete: " + createdCount + " new crates created ("
                    + skippedCount + " existing skipped).");
        } else {
            cdd_sync_log.info("Step 4 complete: no new crates needed ("
                    + skippedCount + " existing skipped).");
        }
    }

    // =========================================================================
    // Crate-name <-> folder mapping helpers (Steps 3 & 4)
    // =========================================================================

    /**
     * Builds a map of crate filename -> direct track paths for every folder
     * in the media library (level 1+). Mirrors the %%‑joined naming convention
     * used by cdd_sync_library. Level 0 (root) is skipped.
     */
    private static Map<String, List<String>> buildCrateFileMap(
            cdd_sync_media_library library, String parentCratePath) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        String rootName = (parentCratePath != null && !parentCratePath.isEmpty())
                ? parentCratePath : "";
        buildCrateFileMapRecursive(library, rootName, 0, result);
        return result;
    }

    /** Recursive walker for buildCrateFileMap. */
    private static void buildCrateFileMapRecursive(cdd_sync_media_library dir,
            String crateName, int level, Map<String, List<String>> result) {
        if (level > 0) {
            result.put(crateName + ".crate", new ArrayList<>(dir.getTracks()));
        }
        for (cdd_sync_media_library child : dir.getChildren()) {
            String childName = crateName.isEmpty()
                    ? child.getDirectory()
                    : crateName + "%%" + child.getDirectory();
            buildCrateFileMapRecursive(child, childName, level + 1, result);
        }
    }

    // =========================================================================
    // Shared helpers
    // =========================================================================

    /**
     * Returns the volume root from a standard _Serato_ path.
     * e.g. /Volumes/Current/_Serato_ -> /Volumes/Current
     */
    private static String getVolumeRoot(String seratoPath) {
        File seratoDir = new File(seratoPath);
        if (seratoDir.getName().equalsIgnoreCase("_Serato_")) {
            return seratoDir.getParent();
        }
        return seratoPath; // fallback: treat seratoPath itself as root
    }

    /**
     * Builds filename (lowercase NFC) -> list of absolute paths from the library.
     * A list is used instead of a single value to detect filename collisions across
     * folders, which would make safe resolution impossible.
     */
    private static Map<String, List<String>> buildLibraryIndex(cdd_sync_media_library library) {
        Map<String, List<String>> index = new HashMap<>();
        List<String> allTracks = new ArrayList<>();
        library.flattenTracks(allTracks);
        for (String path : allTracks) {
            String filename = cdd_sync_binary_utils.getFilename(path);
            index.computeIfAbsent(filename, k -> new ArrayList<>()).add(path);
        }
        return index;
    }

}
