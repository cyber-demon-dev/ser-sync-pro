import java.io.*;
import java.nio.file.*;

/**
 * Handles backup of Serato folder before sync operations.
 * Creates timestamped backup directories in a sibling folder.
 */
public class ser_sync_backup {

    private static final String BACKUP_FOLDER_NAME = "ser-sync-backup";

    /**
     * Creates a backup of the Serato folder.
     * 
     * @param seratoPath Path to the _Serato_ folder (e.g.,
     *                   /Volumes/Current/_Serato_)
     * @return Path to the backup directory, or null if backup failed
     */
    public static String createBackup(String seratoPath) {
        File seratoDir = new File(seratoPath);

        if (!seratoDir.exists() || !seratoDir.isDirectory()) {
            ser_sync_log.error("Serato folder does not exist: " + seratoPath);
            return null;
        }

        // Create backup folder next to _Serato_ folder
        File parentDir = seratoDir.getParentFile();
        File backupRoot = new File(parentDir, BACKUP_FOLDER_NAME);

        // Use raw timestamp (milliseconds since epoch)
        String timestamp = String.valueOf(System.currentTimeMillis());
        String backupName = timestamp + "_Serato_";
        File backupDir = new File(backupRoot, backupName);

        ser_sync_log.info("Creating backup: " + backupDir.getAbsolutePath());

        try {
            // Create backup directory
            if (!backupDir.mkdirs()) {
                ser_sync_log.error("Failed to create backup directory: " + backupDir.getAbsolutePath());
                return null;
            }

            // Copy all files and directories
            long totalBytes = copyDirectory(seratoDir, backupDir);

            String sizeStr = formatSize(totalBytes);
            ser_sync_log.info("Backup complete (" + sizeStr + ")");

            return backupDir.getAbsolutePath();

        } catch (IOException e) {
            ser_sync_log.error("Backup failed: " + e.getMessage());
            ser_sync_log.error(e);
            return null;
        }
    }

    /**
     * Recursively copies a directory and its contents.
     * 
     * @param source Source directory
     * @param target Target directory
     * @return Total bytes copied
     */
    private static long copyDirectory(File source, File target) throws IOException {
        long totalBytes = 0;

        File[] files = source.listFiles();
        if (files == null) {
            return 0;
        }

        for (File file : files) {
            File destFile = new File(target, file.getName());

            if (file.isDirectory()) {
                destFile.mkdirs();
                totalBytes += copyDirectory(file, destFile);
                // Preserve directory timestamp after copying contents
                destFile.setLastModified(file.lastModified());
            } else {
                totalBytes += copyFile(file, destFile);
            }
        }

        // Preserve source directory timestamp
        target.setLastModified(source.lastModified());

        return totalBytes;
    }

    /**
     * Copies a single file.
     * 
     * @param source Source file
     * @param target Target file
     * @return Bytes copied
     */
    private static long copyFile(File source, File target) throws IOException {
        // Copy file with attributes (preserves timestamps)
        Files.copy(source.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        return source.length();
    }

    /**
     * Formats byte count as human-readable size.
     */
    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * Checks if backup is needed (always true for now, could add logic for
     * incremental checks).
     */
    public static boolean isBackupNeeded(String seratoPath) {
        return new File(seratoPath).exists();
    }
}
