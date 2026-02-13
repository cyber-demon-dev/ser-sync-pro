import java.io.*;
import java.text.Normalizer;
import java.util.*;

/**
 * Represents a single Serato crate.
 * Handles reading and writing .crate binary files.
 */
public class ser_sync_crate {

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
    private ser_sync_database database; // Reference to database for path lookup

    public ser_sync_crate() {
    }

    /**
     * Sets the database reference for path lookup.
     * When adding tracks, if the track exists in the database,
     * we use the exact database path to match encoding.
     */
    public void setDatabase(ser_sync_database db) {
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
            // If database is set, try to use Serato's original filename encoding
            // to prevent duplicate entries from encoding mismatches
            String trackToAdd = trackPath;
            if (database != null) {
                String seratoFilename = database.getSeratoFilename(trackPath);
                if (seratoFilename != null) {
                    // Replace filesystem filename with Serato's encoded filename
                    java.io.File f = new java.io.File(trackPath);
                    String dir = f.getParent();
                    if (dir != null) {
                        trackToAdd = dir + java.io.File.separator + seratoFilename;
                    }
                }
            }
            tracks.add(trackToAdd);
        }
    }

    public void addTracks(Collection<String> trackPaths) {
        for (String path : trackPaths) {
            addTrack(path);
        }
    }

    /**
     * Adds tracks with deduplication filtering.
     * Uses Serato's filename encoding from database when available.
     */
    public void addTracksFiltered(Collection<String> trackPaths, ser_sync_track_index index) {
        if (index == null) {
            tracks.addAll(trackPaths);
            return;
        }
        ser_sync_database database = index.getDatabase();

        for (String track : trackPaths) {
            File f = new File(track);
            String size = formatSize(f.length());
            if (!index.shouldSkipTrack(track, size)) {
                // Try to use Serato's original filename encoding to match database
                String trackToAdd = track;
                if (database != null) {
                    String seratoFilename = database.getSeratoFilename(track);
                    if (seratoFilename != null) {
                        // Replace filesystem filename with Serato's encoded filename
                        String dir = f.getParent();
                        if (dir != null) {
                            trackToAdd = dir + File.separator + seratoFilename;
                        }
                    }
                }
                tracks.add(trackToAdd);
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

    public List<String> getColumns() {
        return !columns.isEmpty() ? Collections.unmodifiableList(columns) : Arrays.asList(DEFAULT_COLUMNS);
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
    public static ser_sync_crate readFrom(File inFile) throws ser_sync_exception {
        ser_sync_crate result = new ser_sync_crate();
        ser_sync_input_stream in;

        try {
            in = new ser_sync_input_stream(new BufferedInputStream(new FileInputStream(inFile)));
        } catch (FileNotFoundException e) {
            throw new ser_sync_exception(e);
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
                byte[] typeBytes = new byte[4];
                int read = in.read(typeBytes);
                if (read < 4) {
                    // EOF reached before any tracks found.
                    // This implies an empty crate or one ending after metadata.
                    return result;
                }
                String type = new String(typeBytes);

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
                    in.read(); // Skip final byte (usually '0' but can vary in newer versions)
                } else if ("osrt".equals(type)) {
                    in.readIntegerValue();
                    // Peek at next 4 bytes to determine format:
                    // - Full format: tvcn + sorting name + brev
                    // - Short format: brev only (no sorting name)
                    byte[] peekBytes = new byte[4];
                    in.mark(4);
                    in.read(peekBytes);
                    in.reset();
                    String nextType = new String(peekBytes);
                    if ("tvcn".equals(nextType)) {
                        in.skipExactString("tvcn");
                        int tvcnValue = in.readIntegerValue();
                        result.setSorting(in.readStringUTF16(tvcnValue));
                    }
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

        } catch (Exception e) {
            ser_sync_log.error("Error reading crate file: " + inFile.getAbsolutePath());
            ser_sync_log.error("Parser failure: " + e.getMessage());
            throw new ser_sync_exception("Failed to read crate: " + inFile.getName(), e);
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
    public void writeTo(OutputStream outStream) throws ser_sync_exception {
        ser_sync_output_stream out = new ser_sync_output_stream(outStream);

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
            throw new ser_sync_exception(e);
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
     * Does NOT modify Unicode encoding - we preserve exact bytes from
     * database/filesystem.
     */
    public static String getUniformTrackName(String name) {
        // Forward slashes only
        name = name.replaceAll("\\\\", "/");
        // Remove Windows drive
        name = name.replaceAll("^[a-zA-Z]:\\/", "");
        // Remove macOS /Volumes/DriveName/ prefix
        name = name.replaceAll("^/Volumes/[^/]+/", "");
        // DO NOT normalize Unicode - preserve exact bytes to match database
        return name;
    }

    public void writeTo(File outFile) throws ser_sync_exception {
        try {
            writeTo(new FileOutputStream(outFile));
        } catch (FileNotFoundException e) {
            throw new ser_sync_exception("Error writing to file " + outFile.getName(), e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ser_sync_crate that = (ser_sync_crate) o;

        // Compare versions and sorting
        if (getSortingRev() != that.getSortingRev() ||
                !Objects.equals(getVersion(), that.getVersion()) ||
                !Objects.equals(getSorting(), that.getSorting()) ||
                !Objects.equals(getColumns(), that.getColumns())) {
            return false;
        }

        // Compare tracks using NORMALIZED paths
        // In-memory crates often have absolute paths, while on-disk crates have
        // relative.
        // We must normalize both to ensure "smart write" works correctly.
        if (getTrackCount() != that.getTrackCount()) {
            return false;
        }

        Iterator<String> idx1 = getTracks().iterator();
        Iterator<String> idx2 = that.getTracks().iterator();

        while (idx1.hasNext()) {
            String t1 = getUniformTrackName(idx1.next());
            String t2 = getUniformTrackName(idx2.next());
            if (!t1.equals(t2)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        // Hash code must also use normalized tracks to be consistent with equals
        List<String> normalizedTracks = new ArrayList<>(getTrackCount());
        for (String t : getTracks()) {
            normalizedTracks.add(getUniformTrackName(t));
        }
        return Objects.hash(getVersion(), getSorting(), getSortingRev(), getColumns(), normalizedTracks);
    }
}
