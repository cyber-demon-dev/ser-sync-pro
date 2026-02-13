import java.io.File;
import java.util.*;

/**
 * Builds and manages Serato crate library.
 * Maps filesystem structure to crate hierarchy.
 */
public class ser_sync_library {

    private Map<ser_sync_crate, String> crateFileName = new HashMap<>();
    private ser_sync_crate root;
    private List<ser_sync_crate> crates = new ArrayList<>();
    private List<ser_sync_crate> subCrates = new ArrayList<>();
    private ser_sync_track_index trackIndex;

    public static ser_sync_library createFrom(ser_sync_media_library fsLibrary) {
        return createFrom(fsLibrary, null, null);
    }

    public static ser_sync_library createFrom(ser_sync_media_library fsLibrary, String parentCratePath) {
        return createFrom(fsLibrary, parentCratePath, null);
    }

    public static ser_sync_library createFrom(ser_sync_media_library fsLibrary, String parentCratePath,
            ser_sync_track_index index) {
        ser_sync_library result = new ser_sync_library();
        result.trackIndex = index;

        // If parentCratePath is specified, use it as prefix for all crate names
        String initialCrateName = (parentCratePath != null) ? parentCratePath : "";
        result.buildLibrary(fsLibrary, 0, initialCrateName, false);

        return result;
    }

    private SortedSet<String> buildLibrary(ser_sync_media_library fsLibrary, int level, String crateName,
            boolean includeSubcrateTracks) {
        SortedSet<String> all = new TreeSet<String>();

        // Add tracks from current directory
        all.addAll(fsLibrary.getTracks());

        // Build for every sub-directory
        for (ser_sync_media_library child : fsLibrary.getChildren()) {
            String crateNameNext = crateName.length() > 0 ? crateName + "%%" + child.getDirectory()
                    : child.getDirectory();
            SortedSet<String> children = buildLibrary(child, level + 1, crateNameNext, includeSubcrateTracks);

            if (includeSubcrateTracks) {
                all.addAll(children);
            }
        }

        ser_sync_crate crate = new ser_sync_crate();

        // Set database reference for path encoding lookup
        if (trackIndex != null && trackIndex.getDatabase() != null) {
            crate.setDatabase(trackIndex.getDatabase());
        }

        // Add ALL tracks to crates (crates just contain references)
        // Even if tracks exist in database, we still want them in our new crates
        crate.addTracks(all);

        // Count existing tracks for statistics only (doesn't block from crate)
        if (trackIndex != null) {
            for (String track : all) {
                java.io.File f = new java.io.File(track);
                String size = formatSize(f.length());
                trackIndex.shouldSkipTrack(track, size); // Just counts, doesn't skip
            }
        }

        if (level == 0) {
            root = crate;
        } else if (level == 1) {
            crates.add(crate);
        } else {
            subCrates.add(crate);
        }
        crateFileName.put(crate, crateName + ".crate");

        return all;
    }

    public void writeTo(String seratoLibraryPath, boolean clearLibraryBeforeSync) throws ser_sync_exception {
        if (clearLibraryBeforeSync) {
            ser_sync_file_utils.deleteAllFilesInDirectory(seratoLibraryPath + "/Crates");
            ser_sync_file_utils.deleteAllFilesInDirectory(seratoLibraryPath + "/Subcrates");
            ser_sync_file_utils.deleteFile(seratoLibraryPath + "/database V2");
        }

        int total = crates.size() + subCrates.size();
        int current = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        // Write parent crates
        for (ser_sync_crate crate : crates) {
            current++;
            ser_sync_log.progress("Processing crates", current, total);
            if (writeCrateSmart(crate, seratoLibraryPath, crateFileName.get(crate))) {
                updatedCount++;
            } else {
                skippedCount++;
            }
        }

        // Write sub-crates
        for (ser_sync_crate crate : subCrates) {
            current++;
            ser_sync_log.progress("Processing crates", current, total);
            if (writeCrateSmart(crate, seratoLibraryPath, crateFileName.get(crate))) {
                updatedCount++;
            } else {
                skippedCount++;
            }
        }

        ser_sync_log.progressComplete("Writing crates");

        if (updatedCount > 0) {
            ser_sync_log.info("Updated " + updatedCount + " crates (Skipped " + skippedCount + " unchanged).");
        } else {
            ser_sync_log.info("No crate files needed updating (Skipped " + skippedCount + " unchanged).");
        }
    }

    /**
     * Writes crate to disk only if content has changed.
     * Returns true if written, false if skipped.
     */
    private boolean writeCrateSmart(ser_sync_crate crate, String seratoLibraryPath, String fileName)
            throws ser_sync_exception {
        File crateFile = new File(seratoLibraryPath + "/Subcrates/" + fileName);

        // Check if exists and matches
        if (crateFile.exists()) {
            try {
                ser_sync_crate existing = ser_sync_crate.readFrom(crateFile);
                if (existing.equals(crate)) {
                    return false; // No change needed
                }
            } catch (Exception e) {
                // If we can't read the existing one (corrupt, old version?), force update
            }
        }

        try {
            crateFile.getParentFile().mkdirs();
            crate.writeTo(crateFile);
            return true;
        } catch (ser_sync_exception e) {
            throw new ser_sync_exception("Error serializing crate '" + fileName + "'", e);
        }
    }

    public int getTotalNumberOfCrates() {
        return crates.size();
    }

    public int getTotalNumberOfSubCrates() {
        return subCrates.size();
    }

    public int getTotalTracksWritten() {
        int total = 0;
        for (ser_sync_crate crate : crates) {
            total += crate.getTrackCount();
        }
        for (ser_sync_crate crate : subCrates) {
            total += crate.getTrackCount();
        }
        return total;
    }

    /**
     * Returns all crate names (without .crate extension) for use in neworder.pref
     * generation.
     */
    public List<String> getAllCrateNames() {
        List<String> names = new ArrayList<>();
        for (ser_sync_crate crate : crates) {
            String fileName = crateFileName.get(crate);
            if (fileName != null && fileName.endsWith(".crate")) {
                names.add(fileName.substring(0, fileName.length() - 6));
            }
        }
        for (ser_sync_crate crate : subCrates) {
            String fileName = crateFileName.get(crate);
            if (fileName != null && fileName.endsWith(".crate")) {
                names.add(fileName.substring(0, fileName.length() - 6));
            }
        }
        return names;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024 * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        } else {
            return String.format("%.1fMB", bytes / (1024.0 * 1024));
        }
    }
}
