import javax.swing.*;

/**
 * GUI log window for serato-sync.
 * 
 * @author Roman Alekseenkov (original), refactored for ssync
 */
public class ssync_log_window extends JFrame {

    private JTextArea textArea;

    public ssync_log_window(String title, int width, int height) {
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
class ssync_log_window_handler {

    private ssync_log_window window = null;
    private static ssync_log_window_handler handler = null;

    private ssync_log_window_handler() {
        if (window == null) {
            window = new ssync_log_window("ssync-pro logging window", 650, 350);
        }
    }

    public static synchronized ssync_log_window_handler getInstance() {
        if (handler == null) {
            handler = new ssync_log_window_handler();
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
        // No popup - just exit (main window shows the details)
    }
}
