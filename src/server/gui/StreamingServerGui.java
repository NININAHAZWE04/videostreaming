package server.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import server.StreamingServer;

/**
 * Interface graphique Swing pour le fournisseur de flux vidéo.
 */
public class StreamingServerGui extends JFrame {
    private static final Pattern PORT_PATTERN = Pattern.compile("^\\d{1,5}$");

    private JTextField txtDiaryHost;
    private JTextField txtDiaryPort;
    private JTextField txtVideoTitle;
    private JTextField txtStreamingHost;
    private JTextField txtStreamingPort;
    private JButton btnBrowseFile;
    private JButton btnBrowseFolder;
    private JButton btnStartStreaming;
    private JButton btnClearLogs;
    private JLabel lblSelectedVideo;
    private JLabel lblValidation;
    private JLabel lblStreamsCount;
    private JTable tableActiveStreams;
    private DefaultTableModel tableModel;
    private JTextArea txtLog;

    private File selectedVideoFile;
    private final Map<String, StreamingServer> runningServers;

    public StreamingServerGui() {
        this.runningServers = new LinkedHashMap<>();

        setTitle("Streaming Provider");
        setSize(1024, 760);
        setMinimumSize(new Dimension(920, 680));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                stopAllServers();
            }
        });

        initComponents();
        initValidationBindings();
        updateStartButtonState();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        mainPanel.add(createConfigurationPanel());
        mainPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        mainPanel.add(createVideoSelectionPanel());
        mainPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        mainPanel.add(createActiveStreamsPanel());

        add(new JScrollPane(mainPanel), BorderLayout.CENTER);
        add(createLogPanel(), BorderLayout.SOUTH);
    }

    private JPanel createConfigurationPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Configuration Diary"),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        panel.setBackground(Color.WHITE);

        panel.add(new JLabel("Hôte Diary:"));
        txtDiaryHost = new JTextField("localhost", 16);
        panel.add(txtDiaryHost);

        panel.add(new JLabel("Port Diary:"));
        txtDiaryPort = new JTextField("1099", 8);
        panel.add(txtDiaryPort);

        return panel;
    }

    private JPanel createVideoSelectionPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Nouveau streaming"),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        panel.setBackground(Color.WHITE);

        JPanel browsePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        browsePanel.setBackground(Color.WHITE);

        btnBrowseFile = createActionButton("Parcourir fichier", new Color(41, 128, 185));
        btnBrowseFile.addActionListener(e -> browseForVideoFile());
        browsePanel.add(btnBrowseFile);

        btnBrowseFolder = createActionButton("Choisir depuis dossier", new Color(127, 140, 141));
        btnBrowseFolder.addActionListener(e -> browseVideoFolder());
        browsePanel.add(btnBrowseFolder);

        panel.add(browsePanel);

        JPanel selectedPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        selectedPanel.setBackground(Color.WHITE);
        lblSelectedVideo = new JLabel("Aucune vidéo sélectionnée");
        lblSelectedVideo.setForeground(new Color(39, 174, 96));
        lblSelectedVideo.setFont(lblSelectedVideo.getFont().deriveFont(Font.BOLD));
        selectedPanel.add(lblSelectedVideo);
        panel.add(selectedPanel);

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        titlePanel.setBackground(Color.WHITE);
        titlePanel.add(new JLabel("Titre vidéo:"));
        txtVideoTitle = new JTextField(30);
        titlePanel.add(txtVideoTitle);
        panel.add(titlePanel);

        JPanel portPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        portPanel.setBackground(Color.WHITE);
        portPanel.add(new JLabel("Hôte streaming:"));
        txtStreamingHost = new JTextField(detectLocalHostFallback(), 16);
        portPanel.add(txtStreamingHost);
        portPanel.add(new JLabel("Port streaming:"));
        txtStreamingPort = new JTextField("5000", 8);
        portPanel.add(txtStreamingPort);
        panel.add(portPanel);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        actionPanel.setBackground(Color.WHITE);

        btnStartStreaming = createActionButton("Démarrer le streaming", new Color(39, 174, 96));
        btnStartStreaming.setFont(btnStartStreaming.getFont().deriveFont(Font.BOLD));
        btnStartStreaming.addActionListener(e -> startStreaming());
        actionPanel.add(btnStartStreaming);

        lblValidation = new JLabel(" ");
        lblValidation.setForeground(new Color(192, 57, 43));
        actionPanel.add(lblValidation);

        panel.add(actionPanel);
        return panel;
    }

    private JPanel createActiveStreamsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Streamings actifs"),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        panel.setBackground(Color.WHITE);

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        header.setBackground(Color.WHITE);
        lblStreamsCount = new JLabel("0 flux actif");
        lblStreamsCount.setForeground(new Color(52, 73, 94));
        header.add(lblStreamsCount);
        panel.add(header, BorderLayout.NORTH);

        String[] columnNames = {"Titre", "Hôte", "Port", "Fichier", "URL", "Action"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 5;
            }
        };

        tableActiveStreams = new JTable(tableModel);
        tableActiveStreams.setRowHeight(32);
        tableActiveStreams.setFillsViewportHeight(true);
        tableActiveStreams.getColumnModel().getColumn(0).setPreferredWidth(190);
        tableActiveStreams.getColumnModel().getColumn(1).setPreferredWidth(110);
        tableActiveStreams.getColumnModel().getColumn(2).setPreferredWidth(70);
        tableActiveStreams.getColumnModel().getColumn(3).setPreferredWidth(180);
        tableActiveStreams.getColumnModel().getColumn(4).setPreferredWidth(240);
        tableActiveStreams.getColumnModel().getColumn(5).setPreferredWidth(120);

        tableActiveStreams.getColumn("Action").setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            JButton btn = new JButton("Arrêter");
            btn.setBackground(new Color(192, 57, 43));
            btn.setForeground(Color.WHITE);
            btn.setFocusPainted(false);
            return btn;
        });
        tableActiveStreams.getColumn("Action").setCellEditor(new StopButtonEditor(new JCheckBox()));

        tableActiveStreams.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = tableActiveStreams.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        String url = String.valueOf(tableModel.getValueAt(row, 4));
                        JOptionPane.showMessageDialog(
                            StreamingServerGui.this,
                            "URL du flux:\n" + url,
                            "Information flux",
                            JOptionPane.INFORMATION_MESSAGE
                        );
                    }
                }
            }
        });

        panel.add(new JScrollPane(tableActiveStreams), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Journal d'activité"),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        panel.setBackground(Color.WHITE);

        txtLog = new JTextArea(8, 50);
        txtLog.setEditable(false);
        txtLog.setBackground(new Color(44, 62, 80));
        txtLog.setForeground(new Color(236, 240, 241));
        txtLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        panel.add(new JScrollPane(txtLog), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setBackground(Color.WHITE);
        btnClearLogs = new JButton("Effacer les logs");
        btnClearLogs.addActionListener(e -> txtLog.setText(""));
        actions.add(btnClearLogs);
        panel.add(actions, BorderLayout.SOUTH);

        return panel;
    }

    private void initValidationBindings() {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateStartButtonState();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateStartButtonState();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateStartButtonState();
            }
        };

        txtDiaryHost.getDocument().addDocumentListener(listener);
        txtDiaryPort.getDocument().addDocumentListener(listener);
        txtVideoTitle.getDocument().addDocumentListener(listener);
        txtStreamingHost.getDocument().addDocumentListener(listener);
        txtStreamingPort.getDocument().addDocumentListener(listener);

        txtStreamingPort.addActionListener(e -> startStreaming());
        txtVideoTitle.addActionListener(e -> startStreaming());
    }

    private void updateStartButtonState() {
        String validationError = validateForm(false);
        boolean enabled = validationError == null;
        btnStartStreaming.setEnabled(enabled);
        lblValidation.setText(enabled ? "Prêt" : validationError);
        lblValidation.setForeground(enabled ? new Color(39, 174, 96) : new Color(192, 57, 43));
    }

    private String validateForm(boolean strictFileCheck) {
        if (strictFileCheck) {
            if (selectedVideoFile == null) {
                return "Sélectionnez une vidéo.";
            }
            if (!selectedVideoFile.exists() || !selectedVideoFile.isFile() || !selectedVideoFile.canRead()) {
                return "Le fichier vidéo sélectionné est invalide.";
            }
        }

        String title = txtVideoTitle.getText().trim();
        if (title.isEmpty()) {
            return "Le titre est obligatoire.";
        }
        if (runningServers.containsKey(title)) {
            return "Ce titre est déjà en cours de streaming.";
        }

        String diaryHost = txtDiaryHost.getText().trim();
        String streamHost = txtStreamingHost.getText().trim();
        if (diaryHost.isEmpty() || streamHost.isEmpty()) {
            return "Les hôtes Diary/streaming sont obligatoires.";
        }

        String diaryPortStr = txtDiaryPort.getText().trim();
        String streamPortStr = txtStreamingPort.getText().trim();
        if (!PORT_PATTERN.matcher(diaryPortStr).matches() || !isValidPort(diaryPortStr)) {
            return "Port Diary invalide (1..65535).";
        }
        if (!PORT_PATTERN.matcher(streamPortStr).matches() || !isValidPort(streamPortStr)) {
            return "Port streaming invalide (1..65535).";
        }

        int streamingPort = Integer.parseInt(streamPortStr);
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if ((Integer) tableModel.getValueAt(i, 2) == streamingPort) {
                return "Ce port de streaming est déjà utilisé.";
            }
        }

        return null;
    }

    private void browseForVideoFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Sélectionner une vidéo");
        fileChooser.setFileFilter(new FileNameExtensionFilter(
            "Fichiers vidéo (*.mp4, *.avi, *.mkv, *.mov, *.webm)", "mp4", "avi", "mkv", "mov", "webm"
        ));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            applySelectedVideo(fileChooser.getSelectedFile());
        }
    }

    private void browseVideoFolder() {
        JFileChooser dirChooser = new JFileChooser();
        dirChooser.setDialogTitle("Sélectionner le dossier de vidéos");
        dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int result = dirChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            showVideoListFromFolder(dirChooser.getSelectedFile());
        }
    }

    private void showVideoListFromFolder(File folder) {
        File[] videoFiles = folder.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mkv")
                || lower.endsWith(".mov") || lower.endsWith(".webm");
        });

        if (videoFiles == null || videoFiles.length == 0) {
            JOptionPane.showMessageDialog(this, "Aucune vidéo trouvée dans ce dossier.", "Aucune vidéo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        File selected = (File) JOptionPane.showInputDialog(
            this,
            "Choisissez une vidéo:",
            "Sélection vidéo",
            JOptionPane.PLAIN_MESSAGE,
            null,
            videoFiles,
            videoFiles[0]
        );

        if (selected != null) {
            applySelectedVideo(selected);
        }
    }

    private void applySelectedVideo(File file) {
        selectedVideoFile = file;
        lblSelectedVideo.setText("Sélection: " + file.getName());
        txtVideoTitle.setText(file.getName().replaceFirst("[.][^.]+$", ""));
        logMessage("Vidéo sélectionnée: " + file.getAbsolutePath());
        updateStartButtonState();
    }

    private void startStreaming() {
        String validationError = validateForm(true);
        if (validationError != null) {
            showError(validationError);
            return;
        }

        String title = txtVideoTitle.getText().trim();
        String diaryHost = txtDiaryHost.getText().trim();
        int diaryPort = Integer.parseInt(txtDiaryPort.getText().trim());
        String streamingHost = txtStreamingHost.getText().trim();
        int streamingPort = Integer.parseInt(txtStreamingPort.getText().trim());

        setFormEnabled(false);
        btnStartStreaming.setText("Démarrage...");

        new SwingWorker<StreamingServer, Void>() {
            @Override
            protected StreamingServer doInBackground() throws Exception {
                StreamingServer server = new StreamingServer(
                    selectedVideoFile,
                    title,
                    streamingPort,
                    diaryHost,
                    diaryPort,
                    streamingHost
                );
                server.start();
                return server;
            }

            @Override
            protected void done() {
                try {
                    StreamingServer server = get();
                    runningServers.put(title, server);

                    String videoUrl = "http://" + streamingHost + ":" + streamingPort;
                    tableModel.addRow(new Object[]{
                        title,
                        streamingHost,
                        streamingPort,
                        selectedVideoFile.getName(),
                        videoUrl,
                        "Arrêter"
                    });

                    logMessage("Streaming démarré: " + title + " -> " + videoUrl);
                    resetVideoSelection(streamingPort + 1);
                    refreshStreamsCounter();
                } catch (Exception e) {
                    String message = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
                    showError("Erreur lors du démarrage: " + message);
                    logMessage("Erreur démarrage: " + message);
                } finally {
                    btnStartStreaming.setText("Démarrer le streaming");
                    setFormEnabled(true);
                    updateStartButtonState();
                }
            }
        }.execute();
    }

    private void stopStreaming(int row) {
        if (row < 0 || row >= tableModel.getRowCount()) {
            return;
        }

        String title = String.valueOf(tableModel.getValueAt(row, 0));
        StreamingServer server = runningServers.get(title);
        if (server == null) {
            return;
        }

        server.stop();
        runningServers.remove(title);
        tableModel.removeRow(row);

        logMessage("Streaming arrêté: " + title);
        refreshStreamsCounter();
        updateStartButtonState();
    }

    private void stopAllServers() {
        for (StreamingServer server : runningServers.values()) {
            server.stop();
        }
        runningServers.clear();
        tableModel.setRowCount(0);
        refreshStreamsCounter();
        logMessage("Tous les streamings ont été arrêtés.");
    }

    private void resetVideoSelection(int nextPort) {
        selectedVideoFile = null;
        lblSelectedVideo.setText("Aucune vidéo sélectionnée");
        txtVideoTitle.setText("");
        if (nextPort <= 65_535) {
            txtStreamingPort.setText(String.valueOf(nextPort));
        }
    }

    private void setFormEnabled(boolean enabled) {
        txtDiaryHost.setEnabled(enabled);
        txtDiaryPort.setEnabled(enabled);
        txtVideoTitle.setEnabled(enabled);
        txtStreamingHost.setEnabled(enabled);
        txtStreamingPort.setEnabled(enabled);
        btnBrowseFile.setEnabled(enabled);
        btnBrowseFolder.setEnabled(enabled);
    }

    private void refreshStreamsCounter() {
        int count = tableModel.getRowCount();
        lblStreamsCount.setText(count + (count == 1 ? " flux actif" : " flux actifs"));
    }

    private JButton createActionButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        return button;
    }

    private boolean isValidPort(String value) {
        try {
            int port = Integer.parseInt(value);
            return port >= 1 && port <= 65_535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String detectLocalHostFallback() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    private void logMessage(String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String line = "[" + timestamp + "] " + message;
        System.out.println("[ProviderGUI] " + line);
        SwingUtilities.invokeLater(() -> {
            txtLog.append(line + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    private void showError(String message) {
        System.err.println("[ProviderGUI][ERROR] " + message);
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, message, "Erreur", JOptionPane.ERROR_MESSAGE));
    }

    class StopButtonEditor extends DefaultCellEditor {
        private final JButton button;
        private boolean clicked;
        private int row;

        StopButtonEditor(JCheckBox checkBox) {
            super(checkBox);
            button = new JButton();
            button.setOpaque(true);
            button.setBackground(new Color(192, 57, 43));
            button.setForeground(Color.WHITE);
            button.setFocusPainted(false);
            button.addActionListener(e -> fireEditingStopped());
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            this.row = row;
            button.setText("Arrêter");
            clicked = true;
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            if (clicked) {
                SwingUtilities.invokeLater(() -> stopStreaming(row));
            }
            clicked = false;
            return "Arrêter";
        }

        @Override
        public boolean stopCellEditing() {
            clicked = false;
            return super.stopCellEditing();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | UnsupportedLookAndFeelException e) {
                e.printStackTrace();
            }
            new StreamingServerGui().setVisible(true);
        });
    }
}
