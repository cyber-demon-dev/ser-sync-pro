import java.io.File;
import java.io.IOException;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.concurrent.*;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents a media library on the filesystem.
 * Recursively scans directories for supported audio/video files.
 * Uses parallel processing for faster scanning on multi-core systems.
 */
public class ser_sync_media_library implements Comparable<ser_sync_media_library> {

    private static final Pattern[] MUSIC_FILENAME_PATTERNS = {
            Pattern.compile("(.*)\\.mp3", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.*)\\.flac", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.*)\\.wav", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.*)\\.ogg", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.*)\\.aif", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.*)\\.aiff", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.*)\\.aac", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.*)\\.alac", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.*)\\.m4a", Pattern.CASE_INSENSITIVE)
    };

    private static final Pattern[] VIDEO_FILENAME_PATTERNS = {
            Pattern.compile("(.*)\\.mov", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.*)\\.mp4", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.*)\\.avi", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.*)\\.flv", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.*)\\.mpg", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.*)\\.mpeg", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.*)\\.dv", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.*)\\.qtz", Pattern.CASE_INSENSITIVE)
    };

    // Thread pool for parallel scanning
    private static final int NUM_THREADS = Math.min(4, Runtime.getRuntime().availableProcessors());
    private static final ForkJoinPool SCAN_POOL = new ForkJoinPool(NUM_THREADS);

    private String directory;
    private SortedSet<String> tracks = new TreeSet<String>();
    private SortedSet<ser_sync_media_library> children = new TreeSet<ser_sync_media_library>();

    public ser_sync_media_library(String directory) {
        this.directory = directory;
    }

    public String getDirectory() {
        return directory;
    }

    public SortedSet<String> getTracks() {
        return tracks;
    }

    public SortedSet<ser_sync_media_library> getChildren() {
        return children;
    }

    public int getTotalNumberOfTracks() {
        int result = tracks.size();
        for (ser_sync_media_library childLibrary : children) {
            result += childLibrary.getTotalNumberOfTracks();
        }
        return result;
    }

    public int getTotalNumberOfDirectories() {
        int result = children.size();
        for (ser_sync_media_library childLibrary : children) {
            result += childLibrary.getTotalNumberOfDirectories();
        }
        return result;
    }

    public void flattenTracks(java.util.List<String> list) {
        list.addAll(tracks);
        for (ser_sync_media_library child : children) {
            child.flattenTracks(list);
        }
    }

    public static ser_sync_media_library readFrom(String mediaLibraryPath) {
        ser_sync_media_library result = new ser_sync_media_library(".");
        result.collectAll(mediaLibraryPath);
        return result;
    }

    private void collectAll(String path) {
        File[] all = new File(path).listFiles();
        if (all == null) {
            all = new File[] {};
        }

        // Process audio/video files
        for (File file : all) {
            if (file.isFile() && isMedia(file)) {
                try {
                    tracks.add(file.toPath().toRealPath().toString());
                } catch (IOException e) {
                    tracks.add(file.getAbsolutePath());
                }
            }
        }

        // Collect subdirectories for parallel processing
        List<File> subdirs = new ArrayList<>();
        for (File file : all) {
            if (file.isDirectory()) {
                subdirs.add(file);
            }
        }

        // Process subdirectories in parallel if there are multiple
        if (subdirs.size() > 1) {
            List<Future<ser_sync_media_library>> futures = new ArrayList<>();

            for (File subdir : subdirs) {
                String childDirectory = subdir.getName();
                String childPath = path + "/" + childDirectory;

                futures.add(SCAN_POOL.submit(() -> {
                    ser_sync_media_library child = new ser_sync_media_library(childDirectory);
                    child.collectAll(childPath);
                    return child;
                }));
            }

            // Collect results
            for (Future<ser_sync_media_library> future : futures) {
                try {
                    children.add(future.get());
                } catch (InterruptedException | ExecutionException e) {
                    ser_sync_log.error("Error scanning directory: " + e.getMessage());
                }
            }
        } else {
            // Single or no subdirectory - process sequentially
            for (File subdir : subdirs) {
                String childDirectory = subdir.getName();
                ser_sync_media_library child = new ser_sync_media_library(childDirectory);
                child.collectAll(path + "/" + childDirectory);
                children.add(child);
            }
        }
    }

    private boolean isMedia(File file) {
        String name = file.getName().trim();
        for (Pattern p : MUSIC_FILENAME_PATTERNS) {
            if (p.matcher(name).matches())
                return true;
        }
        for (Pattern p : VIDEO_FILENAME_PATTERNS) {
            if (p.matcher(name).matches())
                return true;
        }
        return false;
    }

    public int compareTo(ser_sync_media_library that) {
        return this.directory.compareTo(that.directory);
    }

    public String toString() {
        return toString(0);
    }

    private String toString(int level) {
        StringBuilder result = new StringBuilder();
        result.append(indent(level)).append(directory).append("\n");
        for (String track : tracks) {
            result.append(indent(level + 1)).append(track).append("\n");
        }
        for (ser_sync_media_library library : children) {
            result.append(library.toString(level + 1));
        }
        return result.toString();
    }

    private String indent(int level) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 2 * level; i++) {
            result.append(' ');
        }
        return result.toString();
    }
}
