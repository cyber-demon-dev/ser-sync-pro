import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Logging utility for ssync-pro.
 * Supports GUI and command-line modes, with optional file logging.
 */
public class ssync_log {

    private static boolean GUI_INITIALIZED = false;
    private static boolean GUI_MODE = true;
    private static ssync_log_window_handler WINDOW_HANDLER;
    private static PrintWriter FILE_WRITER;
    private static PrintWriter DUPE_WRITER;
    private static final String LOG_FILE = "ssync-pro.log";
    private static final String DUPE_FILE = "ssync-dupe-files.log";

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

    private static synchronized void initGui() {
        if (!GUI_INITIALIZED) {
            if (GUI_MODE) {
                try {
                    WINDOW_HANDLER = ssync_log_window_handler.getInstance();
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
            FILE_WRITER = new PrintWriter(new FileWriter(LOG_FILE, false));
            FILE_WRITER.println(getTimestamp() + " [INFO] ssync-pro started");
            FILE_WRITER.flush();
            DUPE_WRITER = new PrintWriter(new FileWriter(DUPE_FILE, false));
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
            FILE_WRITER.println(getTimestamp() + " [INFO] ssync-pro finished");
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
