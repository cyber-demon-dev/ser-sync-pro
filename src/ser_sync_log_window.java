import javax.swing.*;

/**
 * GUI log window for serato-sync.
 * 
 * @author Roman Alekseenkov (original), refactored for ser-sync
 */
public class ser_sync_log_window extends JFrame {

    private JTextArea textArea;

    public ser_sync_log_window(String title, int width, int height) {
        super(title);
        setSize(width, height);
        textArea = new JTextArea();
        JScrollPane pane = new JScrollPane(textArea);
        getContentPane().add(pane);
        setVisible(true);
    }

    /**
     * Appends data to the text area.
     */
    public void showInfo(String data) {
        textArea.append(data);
        this.getContentPane().validate();
    }
}

/**
 * Singleton handler for the log window.
 */
class ser_sync_log_window_handler {

    private ser_sync_log_window window = null;
    private static ser_sync_log_window_handler handler = null;

    private ser_sync_log_window_handler() {
        if (window == null) {
            window = new ser_sync_log_window("ser-sync-pro logging window", 650, 350);
        }
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

    public void fatalError() {
        JOptionPane.showMessageDialog(window,
                "Error occurred. Please inspect the main window with logs for details.",
                "Failure", JOptionPane.ERROR_MESSAGE);
    }

    public void success() {
        JOptionPane.showMessageDialog(window,
                "Sync complete! You can now review the logs.\n\nClose the main window when finished.",
                "Success", JOptionPane.INFORMATION_MESSAGE);
        // Don't exit - keep window open for log review
    }

    public boolean confirm(String message) {
        int result = JOptionPane.showConfirmDialog(window, message, "ser-sync-pro",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        return result == JOptionPane.YES_OPTION;
    }
}
