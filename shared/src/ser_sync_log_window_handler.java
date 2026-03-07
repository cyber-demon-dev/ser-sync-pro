import javax.swing.*;

/**
 * Singleton handler for the log window.
 * In cdd-sync-pro GUI mode, a ser_sync_pro_window is used instead.
 */
public class ser_sync_log_window_handler {

    private ser_sync_log_window window = null;
    private static ser_sync_log_window_handler handler = null;

    private ser_sync_log_window_handler() {
        if (window == null) {
            window = new ser_sync_log_window("cdd-sync-pro logging window", 700, 400);
        }
    }

    /**
     * Package-private constructor for ser_sync_pro_window to inject its own window
     */
    ser_sync_log_window_handler(ser_sync_log_window existingWindow) {
        this.window = existingWindow;
    }

    /** Install a custom handler (used by ser_sync_pro_window) */
    public static synchronized void install(ser_sync_log_window_handler customHandler) {
        handler = customHandler;
    }

    public static synchronized ser_sync_log_window_handler getInstance() {
        if (handler == null) {
            handler = new ser_sync_log_window_handler();
        }
        return handler;
    }

    public synchronized void publish(String message) {
        window.showInfo(message + "\n");
    }

    public void setProgress(String message, int percent) {
        window.setProgress(message, percent);
    }

    public void fatalError() {
        JOptionPane.showMessageDialog(window,
                "Error occurred. Please inspect the main window with logs for details.",
                "Failure", JOptionPane.ERROR_MESSAGE);
    }

    public void success() {
        window.setProgress("", 0); // Clear progress bar
        JOptionPane.showMessageDialog(window,
                "Sync complete! You can now review the logs.\n\nClose the main window when finished.",
                "Success", JOptionPane.INFORMATION_MESSAGE);
        // Don't exit - keep window open for log review
    }

    public boolean confirm(String message) {
        int result = JOptionPane.showConfirmDialog(window, message, "cdd-sync-pro",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        return result == JOptionPane.YES_OPTION;
    }
}
