import javax.swing.*;
import java.awt.*;

/**
 * Base GUI log window for ser-sync apps.
 * Provides a text area for log output and a progress bar.
 * Extended by ser_sync_pro_window for the full config UI.
 * Also used standalone by session-fixer.
 *
 * @author Roman Alekseenkov (original), refactored for ser-sync
 */
public class ser_sync_log_window extends JFrame {

    protected JTextArea textArea;
    protected JProgressBar progressBar;
    protected JLabel progressLabel;

    public ser_sync_log_window(String title, int width, int height) {
        super(title);
        setSize(width, height);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Main panel with BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Text area for logs
        textArea = new JTextArea();
        textArea.setEditable(false);
        JScrollPane pane = new JScrollPane(textArea);
        mainPanel.add(pane, BorderLayout.CENTER);

        // Progress panel at bottom
        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        progressLabel = new JLabel(" ");
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        progressPanel.add(progressLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        mainPanel.add(progressPanel, BorderLayout.SOUTH);

        getContentPane().add(mainPanel);
        setVisible(true);
    }

    /**
     * Appends data to the text area.
     */
    public void showInfo(String data) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(data);
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    /**
     * Updates the progress bar and label.
     */
    public void setProgress(String message, int percent) {
        SwingUtilities.invokeLater(() -> {
            if (message == null || message.isEmpty()) {
                progressBar.setVisible(false);
                progressLabel.setText(" ");
            } else {
                progressBar.setVisible(true);
                progressBar.setValue(percent);
                progressLabel.setText(message);
            }
        });
    }
}

/**
 * Singleton handler for the log window.
 * In ser-sync-pro GUI mode, a ser_sync_pro_window is used instead.
 */
class ser_sync_log_window_handler {

    private ser_sync_log_window window = null;
    private static ser_sync_log_window_handler handler = null;

    private ser_sync_log_window_handler() {
        if (window == null) {
            window = new ser_sync_log_window("ser-sync-pro logging window", 700, 400);
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
        int result = JOptionPane.showConfirmDialog(window, message, "ser-sync-pro",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        return result == JOptionPane.YES_OPTION;
    }
}
