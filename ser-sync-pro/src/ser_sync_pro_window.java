import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Properties;

/**
 * Full config + log GUI window for ser-sync-pro.
 * Extends the base log window with interactive config controls.
 * Dark theme matching Music Timestamp Agent style.
 */
public class ser_sync_pro_window extends ser_sync_log_window {

    // Theme colors
    private static final Color BG_DARK = new Color(0x3C, 0x3F, 0x41);
    private static final Color BG_DARKER = new Color(0x2B, 0x2B, 0x2B);
    private static final Color BG_INPUT = new Color(0x45, 0x49, 0x4A);
    private static final Color BG_LOG = new Color(0x1E, 0x1E, 0x1E);
    private static final Color FG_TEXT = new Color(0xBB, 0xBB, 0xBB);
    private static final Color FG_LABEL = new Color(0xDD, 0xDD, 0xDD);
    private static final Color ACCENT_GREEN = new Color(0x5F, 0xA8, 0x5F);
    private static final Color ACCENT_RED = new Color(0xC7, 0x5F, 0x5F);
    private static final Color BORDER_COLOR = new Color(0x55, 0x58, 0x5A);

    // Path fields
    private JTextField musicFolderField;
    private JTextField seratoPathField;
    private JTextField parentCrateField;

    // Sync option controls
    private JCheckBox backupCheck;
    private JCheckBox skipExistingCheck;
    private JCheckBox fixBrokenPathsCheck;
    private JCheckBox sortCratesCheck;
    private JComboBox<String> dedupModeCombo;

    // Duplicate management controls
    private JCheckBox dupeScanCheck;
    private JComboBox<String> dupeDetectionCombo;
    private JComboBox<String> dupeMoveCombo;

    // Action buttons
    private JButton startButton;
    private JButton cancelButton;

    // Sync state
    private volatile boolean syncRunning = false;
    private volatile boolean syncCancelled = false;
    private Runnable onStartCallback;

    public ser_sync_pro_window() {
        super("ser-sync-pro", 750, 700);

        // Remove the default content from base class
        getContentPane().removeAll();

        // Apply dark theme
        getContentPane().setBackground(BG_DARK);

        // Build the full layout
        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.setBackground(BG_DARK);

        // === Top: Config Panel ===
        JPanel configPanel = buildConfigPanel();
        mainPanel.add(configPanel, BorderLayout.NORTH);

        // === Center: Log Output ===
        JPanel logPanel = buildLogPanel();
        mainPanel.add(logPanel, BorderLayout.CENTER);

        // === Bottom: Progress + Buttons ===
        JPanel bottomPanel = buildBottomPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        getContentPane().add(mainPanel);
        setLocationRelativeTo(null);
        revalidate();
        repaint();
    }

    // ==================== Panel Builders ====================

    private JPanel buildConfigPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_DARK);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 5, 12));

        // --- Path Fields ---
        panel.add(buildPathRow("Music Folder:", musicFolderField = createDarkTextField(), true));
        panel.add(Box.createVerticalStrut(4));
        panel.add(buildPathRow("Serato Path:", seratoPathField = createDarkTextField(), true));
        panel.add(Box.createVerticalStrut(4));
        panel.add(buildPathRow("Parent Crate:", parentCrateField = createDarkTextField(), false));
        panel.add(Box.createVerticalStrut(8));

        // --- Sync Options ---
        JPanel syncPanel = createTitledPanel("Sync Options");
        JPanel syncGrid = new JPanel(new GridLayout(0, 2, 10, 2));
        syncGrid.setBackground(BG_DARK);

        backupCheck = createDarkCheckBox("Backup before sync", true);
        skipExistingCheck = createDarkCheckBox("Skip existing tracks", true);
        fixBrokenPathsCheck = createDarkCheckBox("Fix broken filepaths", false);
        sortCratesCheck = createDarkCheckBox("Sort crates alphabetically", false);

        syncGrid.add(backupCheck);
        syncGrid.add(skipExistingCheck);
        syncGrid.add(fixBrokenPathsCheck);
        syncGrid.add(sortCratesCheck);
        syncPanel.add(syncGrid, BorderLayout.NORTH);

        JPanel dedupRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        dedupRow.setBackground(BG_DARK);
        dedupRow.add(createDarkLabel("Dedup mode:"));
        dedupModeCombo = createDarkComboBox(new String[] { "path", "filename" });
        dedupRow.add(dedupModeCombo);
        syncPanel.add(dedupRow, BorderLayout.SOUTH);

        panel.add(syncPanel);
        panel.add(Box.createVerticalStrut(6));

        // --- Duplicate Management ---
        JPanel dupePanel = createTitledPanel("Duplicate Management");
        JPanel dupeContent = new JPanel();
        dupeContent.setLayout(new BoxLayout(dupeContent, BoxLayout.Y_AXIS));
        dupeContent.setBackground(BG_DARK);

        dupeScanCheck = createDarkCheckBox("Scan for duplicates", false);
        JPanel scanRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        scanRow.setBackground(BG_DARK);
        scanRow.add(dupeScanCheck);
        dupeContent.add(scanRow);

        JPanel dupeDropdowns = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        dupeDropdowns.setBackground(BG_DARK);
        dupeDropdowns.add(createDarkLabel("Detection:"));
        dupeDetectionCombo = createDarkComboBox(new String[] { "name-and-size", "name-only", "off" });
        dupeDropdowns.add(dupeDetectionCombo);
        dupeDropdowns.add(Box.createHorizontalStrut(15));
        dupeDropdowns.add(createDarkLabel("Move mode:"));
        dupeMoveCombo = createDarkComboBox(new String[] { "keep-oldest", "keep-newest", "false" });
        dupeDropdowns.add(dupeMoveCombo);
        dupeContent.add(dupeDropdowns);

        dupePanel.add(dupeContent, BorderLayout.CENTER);
        panel.add(dupePanel);

        return panel;
    }

    private JPanel buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARK);
        panel.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));

        // Reuse base class textArea
        textArea.setBackground(BG_LOG);
        textArea.setForeground(FG_TEXT);
        textArea.setCaretColor(FG_TEXT);
        textArea.setFont(new Font("Menlo", Font.PLAIN, 12));
        textArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(BORDER_COLOR),
                        "Log Output",
                        TitledBorder.LEFT, TitledBorder.TOP,
                        null, FG_LABEL),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        scrollPane.getViewport().setBackground(BG_LOG);

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARK);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 12, 10, 12));

        // Progress
        JPanel progressPanel = new JPanel(new BorderLayout(5, 2));
        progressPanel.setBackground(BG_DARK);

        progressLabel.setForeground(FG_LABEL);
        progressLabel.setText("Ready");
        progressBar.setVisible(false);

        progressPanel.add(progressBar, BorderLayout.NORTH);
        progressPanel.add(progressLabel, BorderLayout.SOUTH);
        panel.add(progressPanel, BorderLayout.NORTH);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 8));
        buttonPanel.setBackground(BG_DARK);

        startButton = new JButton("\u25B6 Start");
        startButton.setBackground(ACCENT_GREEN);
        startButton.setForeground(Color.WHITE);
        startButton.setFocusPainted(false);
        startButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        startButton.setPreferredSize(new Dimension(120, 32));
        startButton.addActionListener(e -> onStartClicked());

        cancelButton = new JButton("\u2715 Cancel");
        cancelButton.setBackground(BG_INPUT);
        cancelButton.setForeground(FG_TEXT);
        cancelButton.setFocusPainted(false);
        cancelButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        cancelButton.setPreferredSize(new Dimension(120, 32));
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> onCancelClicked());

        buttonPanel.add(startButton);
        buttonPanel.add(cancelButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    // ==================== Dark-themed Widget Factories ====================

    private JPanel buildPathRow(String label, JTextField field, boolean withBrowse) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(BG_DARK);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JLabel lbl = createDarkLabel(label);
        lbl.setPreferredSize(new Dimension(100, 24));
        row.add(lbl, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);

        if (withBrowse) {
            JButton browseBtn = new JButton("Browse");
            browseBtn.setBackground(BG_INPUT);
            browseBtn.setForeground(FG_TEXT);
            browseBtn.setFocusPainted(false);
            browseBtn.setPreferredSize(new Dimension(80, 24));
            browseBtn.addActionListener(e -> browseFolder(field));
            row.add(browseBtn, BorderLayout.EAST);
        }

        return row;
    }

    private JTextField createDarkTextField() {
        JTextField field = new JTextField();
        field.setBackground(BG_INPUT);
        field.setForeground(FG_TEXT);
        field.setCaretColor(FG_TEXT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(2, 5, 2, 5)));
        field.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return field;
    }

    private JCheckBox createDarkCheckBox(String text, boolean selected) {
        JCheckBox cb = new JCheckBox(text, selected);
        cb.setBackground(BG_DARK);
        cb.setForeground(FG_LABEL);
        cb.setFocusPainted(false);
        cb.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return cb;
    }

    private JComboBox<String> createDarkComboBox(String[] items) {
        JComboBox<String> combo = new JComboBox<>(items);
        combo.setBackground(BG_INPUT);
        combo.setForeground(FG_TEXT);
        combo.setFont(new Font("SansSerif", Font.PLAIN, 12));
        combo.setPreferredSize(new Dimension(140, 24));
        return combo;
    }

    private JLabel createDarkLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(FG_LABEL);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return label;
    }

    private JPanel createTitledPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setBackground(BG_DARK);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(BORDER_COLOR),
                        title, TitledBorder.LEFT, TitledBorder.TOP,
                        new Font("SansSerif", Font.PLAIN, 12), FG_LABEL),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return panel;
    }

    private void browseFolder(JTextField targetField) {
        JFileChooser chooser = new JFileChooser(targetField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            targetField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    // ==================== Config Load/Save ====================

    /**
     * Populates all controls from the given properties.
     */
    public void loadFromProperties(Properties props) {
        musicFolderField.setText(props.getProperty("music.library.filesystem", ""));
        seratoPathField.setText(props.getProperty("music.library.database", ""));
        parentCrateField.setText(props.getProperty("crate.parent.path", ""));

        backupCheck.setSelected(!"false".equalsIgnoreCase(
                props.getProperty("music.library.database.backup", "true")));
        skipExistingCheck.setSelected(!"false".equalsIgnoreCase(
                props.getProperty("database.skip.existing.tracks", "true")));
        fixBrokenPathsCheck.setSelected("true".equalsIgnoreCase(
                props.getProperty("database.fix.broken.paths", "false")));
        sortCratesCheck.setSelected("true".equalsIgnoreCase(
                props.getProperty("crate.sorting.alphabetical", "false")));

        String dedupMode = props.getProperty("database.dupe.detection.mode", "path");
        selectComboItem(dedupModeCombo, dedupMode);

        dupeScanCheck.setSelected("true".equalsIgnoreCase(
                props.getProperty("harddrive.dupe.scan.enabled", "false")));

        String dupeDetection = props.getProperty("harddrive.dupe.detection.mode", "off");
        selectComboItem(dupeDetectionCombo, dupeDetection);

        String dupeMove = props.getProperty("harddrive.dupe.move.enabled", "false");
        selectComboItem(dupeMoveCombo, dupeMove);
    }

    /**
     * Collects current control values into a Properties object.
     */
    public Properties collectProperties() {
        Properties props = new Properties();
        props.setProperty("mode", "gui");
        props.setProperty("music.library.filesystem", musicFolderField.getText().trim());
        props.setProperty("music.library.database", seratoPathField.getText().trim());

        String parentCrate = parentCrateField.getText().trim();
        if (!parentCrate.isEmpty()) {
            props.setProperty("crate.parent.path", parentCrate);
        }

        props.setProperty("music.library.database.backup", String.valueOf(backupCheck.isSelected()));
        props.setProperty("database.skip.existing.tracks", String.valueOf(skipExistingCheck.isSelected()));
        props.setProperty("database.fix.broken.paths", String.valueOf(fixBrokenPathsCheck.isSelected()));
        props.setProperty("crate.sorting.alphabetical", String.valueOf(sortCratesCheck.isSelected()));
        props.setProperty("database.dupe.detection.mode", (String) dedupModeCombo.getSelectedItem());

        props.setProperty("harddrive.dupe.scan.enabled", String.valueOf(dupeScanCheck.isSelected()));
        props.setProperty("harddrive.dupe.detection.mode", (String) dupeDetectionCombo.getSelectedItem());
        props.setProperty("harddrive.dupe.move.enabled", (String) dupeMoveCombo.getSelectedItem());

        return props;
    }

    /**
     * Saves current control values to the properties file on disk.
     */
    public void saveToFile() {
        Properties props = collectProperties();
        try (FileOutputStream out = new FileOutputStream(ser_sync_config.CONFIG_FILE)) {
            props.store(out, "ser-sync-pro configuration");
        } catch (IOException e) {
            ser_sync_log.error("Failed to save config: " + e.getMessage());
        }
    }

    private void selectComboItem(JComboBox<String> combo, String value) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).equalsIgnoreCase(value)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    // ==================== Action Handlers ====================

    /**
     * Sets the callback to invoke when Start is clicked.
     */
    public void setOnStartCallback(Runnable callback) {
        this.onStartCallback = callback;
    }

    /**
     * Returns true if the user has requested cancellation.
     */
    public boolean isCancelled() {
        return syncCancelled;
    }

    private void onStartClicked() {
        // Validate required fields
        if (musicFolderField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Music Folder is required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (seratoPathField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Serato Path is required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Save settings to file
        saveToFile();

        // Switch to running state
        syncRunning = true;
        syncCancelled = false;
        setControlsEnabled(false);
        startButton.setEnabled(false);
        cancelButton.setEnabled(true);
        cancelButton.setBackground(ACCENT_RED);
        progressLabel.setText("Starting sync...");
        textArea.setText(""); // Clear previous log

        if (onStartCallback != null) {
            onStartCallback.run();
        }
    }

    private void onCancelClicked() {
        syncCancelled = true;
        cancelButton.setEnabled(false);
        progressLabel.setText("Cancelling...");
    }

    /**
     * Called when sync completes (success or error). Re-enables controls.
     */
    public void syncFinished() {
        SwingUtilities.invokeLater(() -> {
            syncRunning = false;
            setControlsEnabled(true);
            startButton.setEnabled(true);
            cancelButton.setEnabled(false);
            cancelButton.setBackground(BG_INPUT);
        });
    }

    private void setControlsEnabled(boolean enabled) {
        musicFolderField.setEnabled(enabled);
        seratoPathField.setEnabled(enabled);
        parentCrateField.setEnabled(enabled);
        backupCheck.setEnabled(enabled);
        skipExistingCheck.setEnabled(enabled);
        fixBrokenPathsCheck.setEnabled(enabled);
        sortCratesCheck.setEnabled(enabled);
        dedupModeCombo.setEnabled(enabled);
        dupeScanCheck.setEnabled(enabled);
        dupeDetectionCombo.setEnabled(enabled);
        dupeMoveCombo.setEnabled(enabled);
    }
}
