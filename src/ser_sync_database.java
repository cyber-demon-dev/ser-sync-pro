import java.io.*;
import java.text.Normalizer;
import java.util.*;

/**
 * Parses Serato database V2 file to extract existing track information.
 * Used for deduplication during sync.
 */
public class ser_sync_database {

    // Track info storage: path -> size
    private Map<String, String> tracksByPath = new HashMap<>();
    private Map<String, String> tracksByFilename = new HashMap<>();

    private int trackCount = 0;

    /**
     * Reads and parses the database V2 file.
     * 
     * @param databasePath Path to the database V2 file
     * @return ser_sync_database instance with parsed tracks, or null if file doesn't
     *         exist
     */
    public static ser_sync_database readFrom(String databasePath) {
        File dbFile = new File(databasePath);
        if (!dbFile.exists()) {
            return null;
        }

        ser_sync_database db = new ser_sync_database();

        try {
            db.parseDatabase(dbFile);
        } catch (Exception e) {
            ser_sync_log.error("Error parsing database V2: " + e.getMessage());
            return null;
        }

        return db;
    }

    /**
     * Parses the binary database V2 file.
     */
    private void parseDatabase(File dbFile) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(dbFile)));

        try {
            // Skip header: vrsn + version string
            skipHeader(in);

            // Read track records
            while (in.available() > 0) {
                try {
                    readTrackRecord(in);
                } catch (EOFException e) {
                    break;
                }
            }
        } finally {
            in.close();
        }
    }

    /**
     * Skips the database header.
     */
    private void skipHeader(DataInputStream in) throws IOException {
        // Read "vrsn"
        byte[] vrsn = new byte[4];
        in.readFully(vrsn);

        if (!"vrsn".equals(new String(vrsn))) {
            throw new IOException("Invalid database V2 format: missing vrsn header");
        }

        // Read length (2 bytes null + 2 bytes length)
        in.skipBytes(2);
        int headerLen = in.readUnsignedShort();

        // Skip the header string
        in.skipBytes(headerLen);
    }

    /**
     * Reads a single track record (otrk block).
     */
    private void readTrackRecord(DataInputStream in) throws IOException {
        // Look for "otrk" marker
        byte[] marker = new byte[4];
        in.readFully(marker);
        String markerStr = new String(marker);

        if (!"otrk".equals(markerStr)) {
            // Not a track record, skip this byte and try again
            // This handles any padding or unknown blocks
            return;
        }

        // Read record length
        int recordLen = in.readInt();

        // Read the record data
        byte[] recordData = new byte[recordLen];
        in.readFully(recordData);

        // Parse the record for pfil and tsiz tags
        parseTrackData(recordData);
    }

    /**
     * Parses track data to extract path and size.
     */
    private void parseTrackData(byte[] data) {
        String path = null;
        String size = null;

        int pos = 0;
        while (pos + 8 < data.length) {
            // Read tag (4 bytes)
            String tag = new String(data, pos, 4);
            pos += 4;

            // Read length (4 bytes, big-endian)
            int len = ((data[pos] & 0xFF) << 24) |
                    ((data[pos + 1] & 0xFF) << 16) |
                    ((data[pos + 2] & 0xFF) << 8) |
                    (data[pos + 3] & 0xFF);
            pos += 4;

            if (pos + len > data.length) {
                break;
            }

            // Extract path (pfil tag)
            if ("pfil".equals(tag)) {
                try {
                    path = new String(data, pos, len, "UTF-16BE");
                } catch (UnsupportedEncodingException e) {
                    // Fallback
                    path = new String(data, pos, len);
                }
            }

            // Extract size (tsiz tag)
            if ("tsiz".equals(tag)) {
                try {
                    size = new String(data, pos, len, "UTF-16BE");
                } catch (UnsupportedEncodingException e) {
                    size = new String(data, pos, len);
                }
            }

            pos += len;
        }

        if (path != null) {
            String normalizedPath = normalizePath(path);
            String key = normalizedPath + (size != null ? "|" + size : "");

            tracksByPath.put(key, path);

            // Also index by filename
            String filename = getFilename(path);
            String filenameKey = filename + (size != null ? "|" + size : "");
            tracksByFilename.put(filenameKey, path);

            trackCount++;
        }
    }

    /**
     * Normalizes a path for comparison.
     * Uses Unicode NFC normalization to handle accented characters consistently.
     */
    private static String normalizePath(String path) {
        if (path == null)
            return "";

        // Unicode NFC normalization - converts decomposed characters to composed form
        // e.g., 'e' + combining accent -> 'é'
        path = Normalizer.normalize(path, Normalizer.Form.NFC);

        // Convert to lowercase
        path = path.toLowerCase();

        // Replace backslashes with forward slashes
        path = path.replace('\\', '/');

        // Remove Windows drive letters
        path = path.replaceAll("^[a-z]:/", "");

        // Remove macOS /Volumes/... prefix
        path = path.replaceAll("^/volumes/[^/]+/", "");

        return path;
    }

    /**
     * Extracts filename from path with Unicode normalization.
     */
    private static String getFilename(String path) {
        if (path == null)
            return "";
        // Apply NFC normalization for consistent Unicode handling
        path = Normalizer.normalize(path, Normalizer.Form.NFC);
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1).toLowerCase() : path.toLowerCase();
    }

    /**
     * Checks if a track exists using path-based matching.
     * 
     * @param trackPath Full path to the track
     * @param fileSize  File size (can be null)
     * @return true if track already exists
     */
    public boolean containsTrackByPath(String trackPath, String fileSize) {
        String normalizedPath = normalizePath(trackPath);
        String key = normalizedPath + (fileSize != null ? "|" + fileSize : "");
        return tracksByPath.containsKey(key);
    }

    /**
     * Checks if a track exists using filename-based matching.
     * 
     * @param trackPath Full path to the track (filename extracted)
     * @param fileSize  File size (can be null)
     * @return true if a track with same filename exists
     */
    public boolean containsTrackByFilename(String trackPath, String fileSize) {
        String filename = getFilename(trackPath);
        String key = filename + (fileSize != null ? "|" + fileSize : "");
        return tracksByFilename.containsKey(key);
    }

    /**
     * Returns total number of tracks indexed.
     */
    public int getTrackCount() {
        return trackCount;
    }

    /**
     * Gets the original database path for a track by filename.
     * This returns the EXACT path encoding from the database.
     * 
     * @param trackPath Path from filesystem
     * @return Original path from database, or null if not found
     */
    public String getOriginalPathByFilename(String trackPath) {
        String filename = getFilename(trackPath);
        // Try with and without size - we just need the path
        for (Map.Entry<String, String> entry : tracksByFilename.entrySet()) {
            if (entry.getKey().startsWith(filename + "|") || entry.getKey().equals(filename)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
