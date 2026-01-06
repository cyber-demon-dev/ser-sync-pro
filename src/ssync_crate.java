import java.io.*;
import java.text.Normalizer;
import java.util.*;

/**
 * Represents a single Serato crate.
 * Handles reading and writing .crate binary files.
 */
public class ssync_crate {

    private static final String DEFAULT_VERSION = "81.0";
    private static final String DEFAULT_SORTING = "song";
    private static final long DEFAULT_SORTING_REV = 1 << 8;
    private static final String[] DEFAULT_COLUMNS = { "song", "artist", "album", "length" };

    private String version;
    private String sorting;
    private long sortingRev = Integer.MIN_VALUE;
    private List<String> columns = new ArrayList<>();
    private List<String> tracks = new ArrayList<>();
    private Set<String> normalizedPaths = new HashSet<>(); // For deduplication
    private ssync_database database; // Reference to database for path lookup

    public ssync_crate() {
    }

    /**
     * Sets the database reference for path lookup.
     * When adding tracks, if the track exists in the database,
     * we use the exact database path to match encoding.
     */
    public void setDatabase(ssync_database db) {
        this.database = db;
    }

    /**
     * Extracts filename for deduplication comparison.
     * Normalizes to NFC for consistent comparison regardless of source encoding.
     */
    private static String normalizeForDedup(String path) {
        // Get just the filename
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String filename = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        // Normalize to NFC for consistent comparison (handles both NFC and NFD inputs)
        return Normalizer.normalize(filename.toLowerCase(), Normalizer.Form.NFC);
    }

    public void addTrack(String trackPath) {
        String key = normalizeForDedup(trackPath);
        if (!normalizedPaths.contains(key)) {
            normalizedPaths.add(key);

            // If we have a database, try to use its path encoding
            String pathToAdd = trackPath;
            if (database != null) {
                String dbPath = database.getOriginalPathByFilename(trackPath);
                if (dbPath != null) {
                    pathToAdd = dbPath;
                }
            }
            tracks.add(pathToAdd);
        }
    }

    public void addTracks(Collection<String> trackPaths) {
        for (String path : trackPaths) {
            addTrack(path);
        }
    }

    /**
     * Adds tracks with deduplication filtering.
     */
    public void addTracksFiltered(Collection<String> trackPaths, ssync_track_index index) {
        if (index == null) {
            tracks.addAll(trackPaths);
            return;
        }
        for (String track : trackPaths) {
            File f = new File(track);
            String size = formatSize(f.length());
            if (!index.shouldSkipTrack(track, size)) {
                tracks.add(track);
            }
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024 * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        } else {
            return String.format("%.1fMB", bytes / (1024.0 * 1024));
        }
    }

    public void setVersion(String version) {
        if (version.length() != 4) {
            throw new IllegalStateException("Version should be 4 characters long");
        }
        this.version = version;
    }

    public String getVersion() {
        return version != null ? version : DEFAULT_VERSION;
    }

    public String getSorting() {
        return sorting != null ? sorting : DEFAULT_SORTING;
    }

    public void setSorting(String sorting) {
        this.sorting = sorting;
    }

    public long getSortingRev() {
        return sortingRev != Integer.MIN_VALUE ? sortingRev : DEFAULT_SORTING_REV;
    }

    public void setSortingRev(long sortingRev) {
        this.sortingRev = sortingRev;
    }

    public void addColumn(String columnName) {
        columns.add(columnName);
    }

    public Collection<String> getColumns() {
        return !columns.isEmpty() ? Collections.unmodifiableCollection(columns) : Arrays.asList(DEFAULT_COLUMNS);
    }

    public Collection<String> getTracks() {
        return Collections.unmodifiableCollection(tracks);
    }

    public int getTrackCount() {
        return tracks.size();
    }

    /**
     * Reads crate from file.
     */
    public static ssync_crate readFrom(File inFile) throws ssync_exception {
        ssync_crate result = new ssync_crate();
        ssync_input_stream in;

        try {
            in = new ssync_input_stream(new FileInputStream(inFile));
        } catch (FileNotFoundException e) {
            throw new ssync_exception(e);
        }

        try {
            // Version header
            in.skipExactString("vrsn");
            in.skipExactByte((byte) 0);
            in.skipExactByte((byte) 0);
            result.setVersion(in.readStringUTF16(8));
            in.skipExactStringUTF16("/Serato ScratchLive Crate");

            // Handle blocks until we hit tracks
            for (;;) {
                String type = in.readString(4);
                if ("otrk".equals(type)) {
                    break;
                }

                if ("ovct".equals(type)) {
                    in.readIntegerValue(); // Skip ovct value
                    in.skipExactString("tvcn");
                    int tvcnValue = in.readIntegerValue();
                    String column = in.readStringUTF16(tvcnValue);
                    result.addColumn(column);
                    in.skipExactString("tvcw");
                    in.readIntegerValue();
                    in.skipExactByte((byte) 0);
                    in.skipExactByte((byte) '0');
                } else if ("osrt".equals(type)) {
                    in.readIntegerValue();
                    in.skipExactString("tvcn");
                    int tvcnValue = in.readIntegerValue();
                    result.setSorting(in.readStringUTF16(tvcnValue));
                    in.skipExactString("brev");
                    result.setSortingRev(in.readLongValue(5));
                }
            }

            // Read tracks
            boolean firstTrack = true;
            for (;;) {
                if (!firstTrack) {
                    boolean eof = in.skipExactString("otrk");
                    if (eof)
                        break;
                }
                firstTrack = false;

                in.readIntegerValue(); // record length
                in.skipExactString("ptrk");
                int nameLength = in.readIntegerValue();
                String trackPath = in.readStringUTF16(nameLength);
                result.addTrack(trackPath);
            }

        } finally {
            try {
                in.close();
            } catch (IOException e) {
                // ignore
            }
        }

        return result;
    }

    /**
     * Writes crate to output stream.
     */
    public void writeTo(OutputStream outStream) throws ssync_exception {
        ssync_output_stream out = new ssync_output_stream(outStream);

        try {
            // Version header
            out.writeBytes("vrsn");
            out.write((byte) 0);
            out.write((byte) 0);
            out.writeUTF16(getVersion());
            out.writeUTF16("/Serato ScratchLive Crate");

            // Sorting
            out.writeBytes("osrt");
            out.writeInt(getSorting().length() * 2 + 17);
            out.writeBytes("tvcn");
            out.writeInt(getSorting().length() * 2);
            out.writeUTF16(getSorting());
            out.writeBytes("brev");
            out.writeLong(getSortingRev(), 5);

            // Columns
            for (String column : getColumns()) {
                out.writeBytes("ovct");
                out.writeInt(column.length() * 2 + 18);
                out.writeBytes("tvcn");
                out.writeInt(column.length() * 2);
                out.writeUTF16(column);
                out.writeBytes("tvcw");
                out.writeInt(2);
                out.write(0);
                out.write('0');
            }

            // Tracks
            for (String trackRaw : getTracks()) {
                String track = getUniformTrackName(trackRaw);
                out.writeBytes("otrk");
                out.writeInt(track.length() * 2 + 8);
                out.writeBytes("ptrk");
                out.writeInt(track.length() * 2);
                out.writeUTF16(track);
            }

        } catch (IOException e) {
            throw new ssync_exception(e);
        } finally {
            try {
                out.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * Normalizes track path for Serato format.
     * Uses raw filesystem encoding to match how Serato originally indexed files.
     */
    private String getUniformTrackName(String name) {
        // Forward slashes only
        name = name.replaceAll("\\\\", "/");
        // Remove Windows drive
        name = name.replaceAll("^[a-zA-Z]:\\/", "");
        // Remove macOS /Volumes/DriveName/ prefix
        name = name.replaceAll("^/Volumes/[^/]+/", "");
        // No Unicode normalization - use raw path as returned by filesystem
        // This matches how Serato originally indexed the files
        return name;
    }

    public void writeTo(File outFile) throws ssync_exception {
        try {
            writeTo(new FileOutputStream(outFile));
        } catch (FileNotFoundException e) {
            throw new ssync_exception("Error writing to file " + outFile.getName(), e);
        }
    }
}
