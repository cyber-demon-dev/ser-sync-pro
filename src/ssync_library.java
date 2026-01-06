import java.io.File;
import java.util.*;

/**
 * Builds and manages Serato crate library.
 * Maps filesystem structure to crate hierarchy.
 */
public class ssync_library {

    private Map<ssync_crate, String> crateFileName = new HashMap<>();
    private ssync_crate root;
    private List<ssync_crate> crates = new ArrayList<>();
    private List<ssync_crate> subCrates = new ArrayList<>();
    private ssync_track_index trackIndex;

    public static ssync_library createFrom(ssync_media_library fsLibrary) {
        return createFrom(fsLibrary, null, null);
    }

    public static ssync_library createFrom(ssync_media_library fsLibrary, String parentCratePath) {
        return createFrom(fsLibrary, parentCratePath, null);
    }

    public static ssync_library createFrom(ssync_media_library fsLibrary, String parentCratePath,
            ssync_track_index index) {
        ssync_library result = new ssync_library();
        result.trackIndex = index;

        // If parentCratePath is specified, use it as prefix for all crate names
        String initialCrateName = (parentCratePath != null) ? parentCratePath : "";
        result.buildLibrary(fsLibrary, 0, initialCrateName, false);

        return result;
    }

    private SortedSet<String> buildLibrary(ssync_media_library fsLibrary, int level, String crateName,
            boolean includeSubcrateTracks) {
        SortedSet<String> all = new TreeSet<String>();

        // Add tracks from current directory
        all.addAll(fsLibrary.getTracks());

        // Build for every sub-directory
        for (ssync_media_library child : fsLibrary.getChildren()) {
            String crateNameNext = crateName.length() > 0 ? crateName + "%%" + child.getDirectory()
                    : child.getDirectory();
            SortedSet<String> children = buildLibrary(child, level + 1, crateNameNext, includeSubcrateTracks);

            if (includeSubcrateTracks) {
                all.addAll(children);
            }
        }

        ssync_crate crate = new ssync_crate();

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

    public void writeTo(String seratoLibraryPath, boolean clearLibraryBeforeSync) throws ssync_exception {
        if (clearLibraryBeforeSync) {
            ssync_file_utils.deleteAllFilesInDirectory(seratoLibraryPath + "/Crates");
            ssync_file_utils.deleteAllFilesInDirectory(seratoLibraryPath + "/Subcrates");
            ssync_file_utils.deleteFile(seratoLibraryPath + "/database V2");
        }

        // Write parent crates
        for (ssync_crate crate : crates) {
            try {
                File crateFile = new File(seratoLibraryPath + "/Subcrates/" + crateFileName.get(crate));
                crateFile.getParentFile().mkdirs();
                crate.writeTo(crateFile);
            } catch (ssync_exception e) {
                throw new ssync_exception("Error serializing crate '" + crateFileName.get(crate) + "'", e);
            }
        }

        // Write sub-crates
        for (ssync_crate crate : subCrates) {
            try {
                File crateFile = new File(seratoLibraryPath + "/Subcrates/" + crateFileName.get(crate));
                crateFile.getParentFile().mkdirs();
                crate.writeTo(crateFile);
            } catch (ssync_exception e) {
                throw new ssync_exception("Error serializing subcrate '" + crateFileName.get(crate) + "'", e);
            }
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
        for (ssync_crate crate : crates) {
            total += crate.getTrackCount();
        }
        for (ssync_crate crate : subCrates) {
            total += crate.getTrackCount();
        }
        return total;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024 * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        } else {
            return String.format("%.1fMB", bytes / (1024.0 * 1024));
        }
    }
}
