import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Fixes broken filepaths in Serato's database V2 file.
 * Updates pfil (path) tags within otrk (track) blocks.
 */
public class ser_sync_database_fixer {

    /**
     * Updates a path in the database V2 file.
     * 
     * @param databasePath Path to the database V2 file
     * @param oldPath      The broken path to find
     * @param newPath      The corrected path to replace with
     * @return true if the path was found and updated
     */
    public static boolean updatePath(String databasePath, String oldPath, String newPath) {
        File dbFile = new File(databasePath);
        if (!dbFile.exists()) {
            return false;
        }

        try {
            // Read entire file
            byte[] data = readFile(dbFile);

            // Convert paths to UTF-16BE for searching
            byte[] oldPathBytes = oldPath.getBytes(StandardCharsets.UTF_16BE);
            byte[] newPathBytes = newPath.getBytes(StandardCharsets.UTF_16BE);

            // Find and replace the path
            byte[] result = replacePfilPath(data, oldPathBytes, newPathBytes);

            if (result != null) {
                // Write back to file
                writeFile(dbFile, result);
                return true;
            }

            return false;
        } catch (IOException e) {
            ser_sync_log.error("Error updating database V2: " + e.getMessage());
            return false;
        }
    }

    /**
     * Batch updates multiple paths in the database V2 file.
     * More efficient than calling updatePath multiple times.
     * 
     * @param databasePath Path to the database V2 file
     * @param pathMappings Map of old paths to new paths
     * @return Number of paths successfully updated
     */
    public static int updatePaths(String databasePath, Map<String, String> pathMappings) {
        File dbFile = new File(databasePath);
        if (!dbFile.exists()) {
            return 0;
        }

        try {
            // Read entire file
            byte[] data = readFile(dbFile);
            int updatedCount = 0;
            int totalPaths = pathMappings.size();
            int processed = 0;
            int progressStep = Math.max(1, totalPaths / 20); // 5% increments
            int nextProgressAt = progressStep;

            for (Map.Entry<String, String> entry : pathMappings.entrySet()) {
                processed++;
                if (processed >= nextProgressAt) {
                    ser_sync_log.progress("Updating database V2", processed, totalPaths);
                    nextProgressAt += progressStep;
                }

                // Normalize paths to match database format (relative, no volume prefix)
                String oldPath = normalizePathForDatabase(entry.getKey());
                String newPath = normalizePathForDatabase(entry.getValue());

                byte[] oldPathBytes = oldPath.getBytes(StandardCharsets.UTF_16BE);
                byte[] newPathBytes = newPath.getBytes(StandardCharsets.UTF_16BE);

                byte[] result = replacePfilPath(data, oldPathBytes, newPathBytes);
                if (result != null) {
                    data = result;
                    updatedCount++;
                }
            }

            ser_sync_log.progressComplete("Updating database V2");

            if (updatedCount > 0) {
                writeFile(dbFile, data);
            }

            return updatedCount;
        } catch (IOException e) {
            ser_sync_log.error("Error updating database V2: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Normalizes a path for database format (relative, no volume prefix).
     * Matches the format Serato uses internally.
     */
    private static String normalizePathForDatabase(String path) {
        if (path == null)
            return "";
        // Forward slashes only
        path = path.replace('\\', '/');
        // Remove Windows drive letters
        path = path.replaceAll("^[a-zA-Z]:/", "");
        // Remove macOS /Volumes/DriveName/ prefix
        path = path.replaceAll("^/Volumes/[^/]+/", "");
        return path;
    }

    /**
     * Finds a pfil tag containing the old path and replaces it with the new path.
     * Adjusts length fields for both pfil and parent otrk block.
     */
    private static byte[] replacePfilPath(byte[] data, byte[] oldPathBytes, byte[] newPathBytes) {
        // Search for "pfil" tag followed by the old path
        byte[] pfilMarker = "pfil".getBytes(StandardCharsets.US_ASCII);

        int pos = 0;
        while (pos < data.length - 8 - oldPathBytes.length) {
            // Look for "pfil" marker
            if (data[pos] == pfilMarker[0] && data[pos + 1] == pfilMarker[1] &&
                    data[pos + 2] == pfilMarker[2] && data[pos + 3] == pfilMarker[3]) {

                // Read pfil length (4 bytes, big-endian)
                int pfilLen = ((data[pos + 4] & 0xFF) << 24) |
                        ((data[pos + 5] & 0xFF) << 16) |
                        ((data[pos + 6] & 0xFF) << 8) |
                        (data[pos + 7] & 0xFF);

                // Check if this pfil contains our old path
                int pathStart = pos + 8;
                if (pathStart + pfilLen <= data.length && pfilLen == oldPathBytes.length) {
                    boolean match = true;
                    for (int i = 0; i < oldPathBytes.length; i++) {
                        if (data[pathStart + i] != oldPathBytes[i]) {
                            match = false;
                            break;
                        }
                    }

                    if (match) {
                        // Found the path! Now we need to:
                        // 1. Find the parent otrk block
                        // 2. Calculate length difference
                        // 3. Rebuild the data with new path

                        int lengthDiff = newPathBytes.length - oldPathBytes.length;

                        // Find the otrk block that contains this pfil
                        int otrkPos = findParentOtrk(data, pos);
                        if (otrkPos == -1) {
                            // Can't find parent otrk, just replace inline if same length
                            if (lengthDiff == 0) {
                                System.arraycopy(newPathBytes, 0, data, pathStart, newPathBytes.length);
                                return data;
                            }
                            pos++;
                            continue;
                        }

                        // Read otrk length
                        int otrkLen = ((data[otrkPos + 4] & 0xFF) << 24) |
                                ((data[otrkPos + 5] & 0xFF) << 16) |
                                ((data[otrkPos + 6] & 0xFF) << 8) |
                                (data[otrkPos + 7] & 0xFF);

                        // Build new data array
                        byte[] newData = new byte[data.length + lengthDiff];

                        // Copy everything before the pfil content
                        System.arraycopy(data, 0, newData, 0, pathStart);

                        // Update pfil length
                        int newPfilLen = newPathBytes.length;
                        newData[pos + 4] = (byte) ((newPfilLen >> 24) & 0xFF);
                        newData[pos + 5] = (byte) ((newPfilLen >> 16) & 0xFF);
                        newData[pos + 6] = (byte) ((newPfilLen >> 8) & 0xFF);
                        newData[pos + 7] = (byte) (newPfilLen & 0xFF);

                        // Copy new path
                        System.arraycopy(newPathBytes, 0, newData, pathStart, newPathBytes.length);

                        // Copy everything after the old path
                        int afterOldPath = pathStart + oldPathBytes.length;
                        System.arraycopy(data, afterOldPath, newData, pathStart + newPathBytes.length,
                                data.length - afterOldPath);

                        // Update otrk length
                        int newOtrkLen = otrkLen + lengthDiff;
                        newData[otrkPos + 4] = (byte) ((newOtrkLen >> 24) & 0xFF);
                        newData[otrkPos + 5] = (byte) ((newOtrkLen >> 16) & 0xFF);
                        newData[otrkPos + 6] = (byte) ((newOtrkLen >> 8) & 0xFF);
                        newData[otrkPos + 7] = (byte) (newOtrkLen & 0xFF);

                        return newData;
                    }
                }
            }
            pos++;
        }

        return null; // Path not found
    }

    /**
     * Finds the otrk block that contains the given position.
     */
    private static int findParentOtrk(byte[] data, int targetPos) {
        byte[] otrkMarker = "otrk".getBytes(StandardCharsets.US_ASCII);

        // Search backwards from targetPos for the nearest otrk
        // We need to scan forward from the beginning and track otrk positions
        int lastOtrkPos = -1;
        int pos = 0;

        while (pos < targetPos && pos < data.length - 8) {
            if (data[pos] == otrkMarker[0] && data[pos + 1] == otrkMarker[1] &&
                    data[pos + 2] == otrkMarker[2] && data[pos + 3] == otrkMarker[3]) {

                int otrkLen = ((data[pos + 4] & 0xFF) << 24) |
                        ((data[pos + 5] & 0xFF) << 16) |
                        ((data[pos + 6] & 0xFF) << 8) |
                        (data[pos + 7] & 0xFF);

                int otrkEnd = pos + 8 + otrkLen;

                // Check if targetPos is within this otrk block
                if (targetPos >= pos && targetPos < otrkEnd) {
                    return pos;
                }

                lastOtrkPos = pos;
                // Skip to end of this otrk block
                pos = otrkEnd;
            } else {
                pos++;
            }
        }

        return lastOtrkPos;
    }

    private static byte[] readFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = fis.read(data);
            if (read != data.length) {
                throw new IOException("Could not read entire file");
            }
            return data;
        }
    }

    private static void writeFile(File file, byte[] data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }
}
