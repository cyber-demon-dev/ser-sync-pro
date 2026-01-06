import java.io.File;

/**
 * File and directory utilities for serato-sync.
 */
public class ser_sync_file_utils {

    /**
     * Deletes all files in a directory (not subdirectories).
     */
    @SuppressWarnings({ "ResultOfMethodCallIgnored" })
    public static void deleteAllFilesInDirectory(String directoryPath) {
        File directory = new File(directoryPath);
        if (directory.isDirectory()) {
            File[] all = directory.listFiles();
            if (all != null) {
                for (File file : all) {
                    if (file.isFile()) {
                        file.delete();
                    }
                }
            }
        }
    }

    /**
     * Deletes a single file.
     */
    @SuppressWarnings({ "ResultOfMethodCallIgnored" })
    public static void deleteFile(String filePath) {
        new File(filePath).delete();
    }
}
