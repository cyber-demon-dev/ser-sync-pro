import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

/**
 * Logging utility for ser-sync-pro.
 * Supports GUI and command-line modes, with optional file logging.
 */
public class ser_sync_log {

    private static boolean GUI_INITIALIZED = false;
    private static boolean GUI_MODE = true;
    private static ser_sync_log_window_handler WINDOW_HANDLER;
    private static PrintWriter FILE_WRITER;
    private static PrintWriter DUPE_WRITER;
    private static String LOG_FILE = "ser-sync-pro.log";
    private static final String DUPE_FILE = "ser-sync-dupe-files.log";
    private static String LOG_DIR = null; // null = use CWD-relative "logs/"

    /**
     * Sets the log file name. Must be called before any logging methods.
     */
    public static synchronized void setLogFile(String filename) {
        LOG_FILE = filename;
    }

    /**
     * Sets the log directory path. If set, logs are written to this directory
     * instead of CWD-relative "logs/". Must be called before any logging methods.
     */
    public static synchronized void setLogDirectory(String directory) {
        LOG_DIR = directory;
    }

    public static void info(String message) {
        initGui();
        String timestamped = getTimestamp() + " [INFO] " + message;

        if (GUI_MODE) {
            WINDOW_HANDLER.publish(message);
        } else {
            System.out.println(message);
            System.out.flush();
        }
        writeToFile(timestamped);
    }

    // Progress tracking state
    private static long progressStartTime = 0;
    private static String lastProgressTask = "";
    private static int lastProgressPercent = -1;

    /**
     * Displays progress with percentage and estimated time remaining.
     * Only updates display when percentage changes to avoid console spam.
     * 
     * @param task    Name of the task (e.g., "Scanning library", "Writing crates")
     * @param current Current item number (1-based)
     * @param total   Total number of items
     */
    public static void progress(String task, int current, int total) {
        if (total <= 0)
            return;

        initGui();

        // Reset start time if task changed
        if (!task.equals(lastProgressTask)) {
            progressStartTime = System.currentTimeMillis();
            lastProgressTask = task;
            lastProgressPercent = -1;
        }

        // Use long to avoid integer overflow for large files (current * 100 can exceed
        // int max)
        int percent = (int) ((long) current * 100 / total);

        // Only update if percentage changed (reduces output spam)
        if (percent == lastProgressPercent && current < total) {
            return;
        }
        lastProgressPercent = percent;

        // Calculate ETA
        String eta = "";
        if (current > 0 && progressStartTime > 0) {
            long elapsed = System.currentTimeMillis() - progressStartTime;
            if (elapsed > 500 && percent > 0 && percent < 100) { // Only show ETA after 500ms
                long estimatedTotal = (elapsed * 100) / percent;
                long remaining = estimatedTotal - elapsed;
                if (remaining > 1000) {
                    long seconds = remaining / 1000;
                    if (seconds > 60) {
                        eta = " (ETA: " + (seconds / 60) + "m " + (seconds % 60) + "s)";
                    } else {
                        eta = " (ETA: " + seconds + "s)";
                    }
                }
            }
        }

        String message = task + ": " + percent + "% (" + current + "/" + total + ")" + eta;

        if (GUI_MODE) {
            WINDOW_HANDLER.setProgress(message, percent);
        } else {
            // Use carriage return to update in place for CLI
            System.out.print("\r" + message + "          ");
            if (current >= total) {
                System.out.println(); // New line when complete
            }
            System.out.flush();
        }
    }

    /**
     * Clears progress display after task completion.
     */
    public static void progressComplete(String task) {
        lastProgressPercent = -1;
        lastProgressTask = "";
        progressStartTime = 0;

        if (GUI_MODE) {
            WINDOW_HANDLER.setProgress("", 0);
        }
    }

    public static void dupe(String message) {
        initGui();
        if (DUPE_WRITER != null) {
            DUPE_WRITER.println(message);
            DUPE_WRITER.flush();
        }
    }

    public static void error(String message) {
        initGui();
        String timestamped = getTimestamp() + " [ERROR] " + message;

        if (GUI_MODE) {
            WINDOW_HANDLER.publish(message);
        } else {
            System.err.println(message);
            System.err.flush();
        }
        writeToFile(timestamped);
    }

    public static void error(Exception e) {
        initGui();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(out));
        String stackTrace = out.toString();

        if (GUI_MODE) {
            WINDOW_HANDLER.publish(stackTrace);
        } else {
            e.printStackTrace(System.err);
            System.err.flush();
        }
        writeToFile(getTimestamp() + " [ERROR] " + stackTrace);
    }

    public static void fatalError() {
        initGui();
        if (GUI_MODE) {
            WINDOW_HANDLER.fatalError();
        }
        closeLogFile();
        System.exit(-1);
    }

    public static void success() {
        initGui();
        if (GUI_MODE) {
            WINDOW_HANDLER.success();
            // Don't exit - keep window open for log review
        } else {
            closeLogFile();
            System.exit(0);
        }
    }

    public static boolean confirm(String message) {
        initGui();
        if (GUI_MODE) {
            return WINDOW_HANDLER.confirm(message);
        } else {
            System.out.println(message + " [y/n]: ");
            System.out.flush();
            try (Scanner scanner = new Scanner(System.in)) {
                String input = scanner.nextLine().trim().toLowerCase();
                return input.equals("y") || input.equals("yes");
            }
        }
    }

    private static synchronized void initGui() {
        if (!GUI_INITIALIZED) {
            if (GUI_MODE) {
                try {
                    WINDOW_HANDLER = ser_sync_log_window_handler.getInstance();
                } catch (Exception e) {
                    GUI_MODE = false;
                }
            }
            initLogFile();
            GUI_INITIALIZED = true;
        }
    }

    private static void initLogFile() {
        try {
            // Use configured log directory or fall back to CWD-relative "logs/"
            File logsDir = LOG_DIR != null ? new File(LOG_DIR) : new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }

            // Create timestamped log filename
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String logPath = new File(logsDir, "ser-sync-pro-" + timestamp + ".log").getAbsolutePath();

            FILE_WRITER = new PrintWriter(new FileWriter(logPath, false));
            FILE_WRITER.println(getTimestamp() + " [INFO] ser-sync-pro started");
            FILE_WRITER.flush();
            String dupeLogPath = new File(logsDir, "ser-sync-dupe-files-" + timestamp + ".log").getAbsolutePath();
            DUPE_WRITER = new PrintWriter(new FileWriter(dupeLogPath, false));
        } catch (IOException e) {
            // Can't write log file - continue without it
        }
    }

    private static void writeToFile(String message) {
        if (FILE_WRITER != null) {
            FILE_WRITER.println(message);
            FILE_WRITER.flush();
        }
    }

    private static void closeLogFile() {
        if (FILE_WRITER != null) {
            FILE_WRITER.println(getTimestamp() + " [INFO] ser-sync-pro finished");
            FILE_WRITER.close();
        }
        if (DUPE_WRITER != null) {
            DUPE_WRITER.close();
        }
    }

    private static String getTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    public static synchronized void setMode(boolean guiMode) {
        GUI_MODE = guiMode;
    }
}
