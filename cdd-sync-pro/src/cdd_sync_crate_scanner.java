import java.io.*;
import java.util.*;

/**
 * Scans existing .crate files to extract track information.
 * Used in addition to database V2 for comprehensive deduplication.
 */
public class cdd_sync_crate_scanner {

    private Map<String, String> tracksByPath = new HashMap<>();
    private Map<String, String> tracksByFilename = new HashMap<>();

    private int trackCount = 0;
    private int crateCount = 0;

    /**
     * Scans all .crate files in the Subcrates directory.
     * 
     * @param seratoPath Path to the _Serato_ folder
     * @return cdd_sync_crate_scanner instance with parsed tracks
     */
    public static cdd_sync_crate_scanner scanFrom(String seratoPath) {
        cdd_sync_crate_scanner scanner = new cdd_sync_crate_scanner();

        File subcratesDir = new File(seratoPath + "/Subcrates");
        if (!subcratesDir.exists() || !subcratesDir.isDirectory()) {
            return scanner;
        }

        File[] crateFiles = subcratesDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".crate");
            }
        });

        if (crateFiles == null) {
            return scanner;
        }

        for (File crateFile : crateFiles) {
            scanner.parseCrateFile(crateFile);
        }

        return scanner;
    }

    /**
     * Parses a single .crate file.
     */
    private void parseCrateFile(File crateFile) {
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(crateFile)));

            try {
                // Skip header
                skipCrateHeader(in);

                // Read track entries
                while (in.available() > 0) {
                    try {
                        readCrateTrack(in);
                    } catch (EOFException e) {
                        break;
                    }
                }

                crateCount++;

            } finally {
                in.close();
            }
        } catch (IOException e) {
            cdd_sync_log.error("Skipping unreadable crate: " + crateFile.getName() + " — " + e.getMessage());
        }
    }

    /**
     * Skips the crate file header.
     */
    private void skipCrateHeader(DataInputStream in) throws IOException {
        // Read "vrsn"
        byte[] vrsn = new byte[4];
        in.readFully(vrsn);

        if (!"vrsn".equals(new String(vrsn))) {
            throw new IOException("Invalid crate format");
        }

        // Skip null bytes and read header length
        in.skipBytes(2);
        int headerLen = in.readUnsignedShort();
        in.skipBytes(headerLen);

        // Skip osrt, ovct blocks until we hit otrk
        while (in.available() > 0) {
            byte[] tag = new byte[4];
            in.readFully(tag);
            String tagStr = new String(tag);

            if ("otrk".equals(tagStr)) {
                // Found first track, read its length and process
                int len = in.readInt();
                processTrackBlock(in, len);
                return;
            } else if ("osrt".equals(tagStr) || "ovct".equals(tagStr)) {
                // Skip this block
                int len = in.readInt();
                in.skipBytes(len);
            } else {
                // Unknown block, try to skip
                break;
            }
        }
    }

    /**
     * Reads a track entry from the crate.
     */
    private void readCrateTrack(DataInputStream in) throws IOException {
        // Look for "otrk"
        byte[] tag = new byte[4];
        int read = in.read(tag);
        if (read < 4) {
            throw new EOFException();
        }

        if (!"otrk".equals(new String(tag))) {
            return;
        }

        int len = in.readInt();
        processTrackBlock(in, len);
    }

    /**
     * Processes a track block to extract the path.
     */
    private void processTrackBlock(DataInputStream in, int blockLen) throws IOException {
        byte[] data = new byte[blockLen];
        in.readFully(data);

        // Look for "ptrk" tag within the block
        for (int i = 0; i < data.length - 8; i++) {
            if (data[i] == 'p' && data[i + 1] == 't' && data[i + 2] == 'r' && data[i + 3] == 'k') {
                // Found ptrk tag
                int len = cdd_sync_binary_utils.readInt(data, i + 4);

                if (i + 8 + len <= data.length) {
                    try {
                        String path = new String(data, i + 8, len, "UTF-16BE");
                        addTrack(path);
                    } catch (UnsupportedEncodingException e) {
                        // Skip
                    }
                }
                break;
            }
        }
    }

    /**
     * Adds a track to the index.
     */
    private void addTrack(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }

        String normalizedPath = normalizePath(path);
        tracksByPath.put(normalizedPath, path);

        String filename = cdd_sync_binary_utils.getFilename(path);
        tracksByFilename.put(filename, path);

        trackCount++;
    }

    /**
     * Normalizes a path for comparison.
     * Delegates to shared utility for consistent behavior.
     */
    private static String normalizePath(String path) {
        return cdd_sync_binary_utils.normalizePath(path);
    }

    /**
     * Checks if a track exists by path.
     */
    public boolean containsTrackByPath(String trackPath) {
        return tracksByPath.containsKey(normalizePath(trackPath));
    }

    /**
     * Checks if a track exists by filename.
     */
    public boolean containsTrackByFilename(String trackPath) {
        return tracksByFilename.containsKey(cdd_sync_binary_utils.getFilename(trackPath));
    }

    public int getTrackCount() {
        return trackCount;
    }

    public int getCrateCount() {
        return crateCount;
    }
}
