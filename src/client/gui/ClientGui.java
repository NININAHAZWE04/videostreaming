package client.gui;

import diary.Diary;
import diary.VideoInfo;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import javax.swing.table.DefaultTableModel;

/**
 * Interface graphique Swing pour le client de lecture.
 */
public class ClientGui extends JFrame {
    private static final Pattern PORT_PATTERN = Pattern.compile("^\\d{1,5}$");

    private JTextField txtDiaryHost;
    private JTextField txtDiaryPort;
    private JButton btnConnect;
    private JButton btnDisconnect;
    private JButton btnRefresh;
    private JButton btnClearLogs;
    private JTable tableVideos;
    private DefaultTableModel tableModel;
    private JTextArea txtLog;
    private JLabel lblStatus;
    private JLabel lblVideoCount;

    private Diary diary;
    private boolean connected;

    public ClientGui() {
        setTitle("Video Viewer");
        setSize(980, 720);
        setMinimumSize(new Dimension(860, 620));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        updateConnectionUiState(false);
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(createConnectionPanel(), BorderLayout.NORTH);
        add(createVideoPanel(), BorderLayout.CENTER);
        add(createLogPanel(), BorderLayout.SOUTH);
    }

    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Connexion au Diary"),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        panel.setBackground(Color.WHITE);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        left.setBackground(Color.WHITE);

        left.add(new JLabel("Hôte:"));
        txtDiaryHost = new JTextField("localhost", 16);
        txtDiaryHost.setToolTipText("Adresse du serveur Diary");
        left.add(txtDiaryHost);

        left.add(new JLabel("Port:"));
        txtDiaryPort = new JTextField("1099", 7);
        txtDiaryPort.setToolTipText("Port RMI du Diary");
        txtDiaryPort.addActionListener(e -> connectToDiary());
        left.add(txtDiaryPort);

        btnConnect = createActionButton("Connecter", new Color(41, 128, 185));
        btnConnect.addActionListener(e -> connectToDiary());
        left.add(btnConnect);

        btnDisconnect = createActionButton("Déconnecter", new Color(192, 57, 43));
        btnDisconnect.addActionListener(e -> disconnectFromDiary());
        left.add(btnDisconnect);

        lblStatus = new JLabel("● Non connecté");
        lblStatus.setFont(lblStatus.getFont().deriveFont(Font.BOLD));
        left.add(lblStatus);

        panel.add(left, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createVideoPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Vidéos disponibles"),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        panel.setBackground(Color.WHITE);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setBackground(Color.WHITE);
        lblVideoCount = new JLabel("0 vidéo");
        lblVideoCount.setForeground(new Color(52, 73, 94));
        left.add(lblVideoCount);
        header.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setBackground(Color.WHITE);
        btnRefresh = createActionButton("Actualiser", new Color(22, 160, 133));
        btnRefresh.addActionListener(e -> refreshVideoList());
        right.add(btnRefresh);
        header.add(right, BorderLayout.EAST);

        panel.add(header, BorderLayout.NORTH);

        String[] columns = {"Titre", "Hôte", "Port", "URL", "Action"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4;
            }
        };

        tableVideos = new JTable(tableModel);
        tableVideos.setRowHeight(32);
        tableVideos.setFillsViewportHeight(true);
        tableVideos.getColumnModel().getColumn(0).setPreferredWidth(250);
        tableVideos.getColumnModel().getColumn(1).setPreferredWidth(120);
        tableVideos.getColumnModel().getColumn(2).setPreferredWidth(65);
        tableVideos.getColumnModel().getColumn(3).setPreferredWidth(240);
        tableVideos.getColumnModel().getColumn(4).setPreferredWidth(120);

        tableVideos.getColumn("Action").setCellRenderer((table, value, isSelected, hasFocus, row, column) -> {
            JButton button = new JButton("Regarder");
            button.setBackground(new Color(230, 126, 34));
            button.setForeground(Color.WHITE);
            button.setFocusPainted(false);
            return button;
        });
        tableVideos.getColumn("Action").setCellEditor(new WatchButtonEditor(new JCheckBox()));

        tableVideos.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    int row = tableVideos.getSelectedRow();
                    if (row >= 0) {
                        watchVideo(row);
                    }
                }
            }
        });

        panel.add(new JScrollPane(tableVideos), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Journal d'activité"),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        panel.setBackground(Color.WHITE);

        txtLog = new JTextArea(7, 50);
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

    private void connectToDiary() {
        String host = txtDiaryHost.getText().trim();
        String portText = txtDiaryPort.getText().trim();

        if (host.isEmpty()) {
            showError("L'hôte Diary est obligatoire.");
            return;
        }
        if (!PORT_PATTERN.matcher(portText).matches() || !isValidPort(portText)) {
            showError("Port Diary invalide (1..65535).");
            return;
        }

        int port = Integer.parseInt(portText);
        setConnectingState(true);
        logMessage("Connexion au Diary " + host + ":" + port + "...");

        new SwingWorker<Diary, Void>() {
            @Override
            protected Diary doInBackground() throws Exception {
                Registry registry = LocateRegistry.getRegistry(host, port);
                return (Diary) registry.lookup("Diary");
            }

            @Override
            protected void done() {
                try {
                    diary = get();
                    connected = true;
                    updateConnectionUiState(true);
                    logMessage("Connecté au Diary.");
                    refreshVideoList();
                } catch (Exception e) {
                    connected = false;
                    diary = null;
                    updateConnectionUiState(false);
                    String msg = unwrapMessage(e);
                    showError("Connexion impossible: " + msg);
                    logMessage("Erreur connexion: " + msg);
                }
            }
        }.execute();
    }

    private void disconnectFromDiary() {
        if (!connected) {
            return;
        }

        diary = null;
        connected = false;
        tableModel.setRowCount(0);
        updateVideoCount();
        updateConnectionUiState(false);
        logMessage("Déconnecté du Diary.");
    }

    private void refreshVideoList() {
        if (!connected || diary == null) {
            showError("Connectez-vous d'abord au Diary.");
            return;
        }

        btnRefresh.setEnabled(false);
        btnRefresh.setText("Actualisation...");
        logMessage("Actualisation des vidéos...");

        new SwingWorker<List<VideoInfo>, Void>() {
            @Override
            protected List<VideoInfo> doInBackground() throws Exception {
                return diary.listAllVideos();
            }

            @Override
            protected void done() {
                try {
                    List<VideoInfo> videos = get();
                    tableModel.setRowCount(0);
                    for (VideoInfo video : videos) {
                        String url = "http://" + video.getHost() + ":" + video.getPort();
                        tableModel.addRow(new Object[]{
                            video.getTitle(),
                            video.getHost(),
                            video.getPort(),
                            url,
                            "Regarder"
                        });
                    }
                    updateVideoCount();
                    logMessage("Liste actualisée: " + videos.size() + " vidéo(s).");
                } catch (Exception e) {
                    String msg = unwrapMessage(e);
                    showError("Erreur d'actualisation: " + msg);
                    logMessage("Erreur actualisation: " + msg);

                    if (e.getCause() instanceof RemoteException) {
                        disconnectFromDiary();
                    }
                } finally {
                    if (connected) {
                        btnRefresh.setEnabled(true);
                        btnRefresh.setText("Actualiser");
                    }
                }
            }
        }.execute();
    }

    private void watchVideo(int row) {
        if (row < 0 || row >= tableModel.getRowCount()) {
            return;
        }

        String title = String.valueOf(tableModel.getValueAt(row, 0));
        String videoUrl = String.valueOf(tableModel.getValueAt(row, 3));

        logMessage("Lecture demandée: " + title + " -> " + videoUrl);

        try {
            ProcessBuilder pb = new ProcessBuilder("vlc", videoUrl);
            pb.start();
            logMessage("VLC lancé.");
        } catch (IOException e) {
            logMessage("VLC indisponible, tentative navigateur...");
            if (!openInBrowser(videoUrl)) {
                showError("Impossible de lancer VLC ou le navigateur. Vérifiez l'installation de VLC.");
                logMessage("Lecture impossible: " + e.getMessage());
            }
        }
    }

    private boolean openInBrowser(String url) {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
            logMessage("Ouverture via navigateur.");
            return true;
        } catch (IOException | URISyntaxException e) {
            return false;
        }
    }

    private void setConnectingState(boolean connecting) {
        btnConnect.setEnabled(!connecting);
        btnConnect.setText(connecting ? "Connexion..." : "Connecter");
        txtDiaryHost.setEnabled(!connecting && !connected);
        txtDiaryPort.setEnabled(!connecting && !connected);
    }

    private void updateConnectionUiState(boolean isConnected) {
        lblStatus.setText(isConnected ? "● Connecté" : "● Non connecté");
        lblStatus.setForeground(isConnected ? new Color(39, 174, 96) : new Color(192, 57, 43));

        btnConnect.setEnabled(!isConnected);
        btnConnect.setText("Connecter");
        btnDisconnect.setEnabled(isConnected);
        btnRefresh.setEnabled(isConnected);

        txtDiaryHost.setEnabled(!isConnected);
        txtDiaryPort.setEnabled(!isConnected);
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

    private void updateVideoCount() {
        int count = tableModel.getRowCount();
        lblVideoCount.setText(count + (count <= 1 ? " vidéo" : " vidéos"));
    }

    private String unwrapMessage(Exception exception) {
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return (message == null || message.isBlank()) ? root.getClass().getSimpleName() : message;
    }

    private void logMessage(String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String line = "[" + timestamp + "] " + message;
        System.out.println("[ClientGUI] " + line);
        SwingUtilities.invokeLater(() -> {
            txtLog.append(line + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    private void showError(String message) {
        System.err.println("[ClientGUI][ERROR] " + message);
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, message, "Erreur", JOptionPane.ERROR_MESSAGE));
    }

    class WatchButtonEditor extends DefaultCellEditor {
        private final JButton button;
        private boolean clicked;
        private int row;

        WatchButtonEditor(JCheckBox checkBox) {
            super(checkBox);
            button = new JButton();
            button.setOpaque(true);
            button.setBackground(new Color(230, 126, 34));
            button.setForeground(Color.WHITE);
            button.setFocusPainted(false);
            button.addActionListener(e -> fireEditingStopped());
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            this.row = row;
            button.setText("Regarder");
            clicked = true;
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            if (clicked) {
                SwingUtilities.invokeLater(() -> watchVideo(row));
            }
            clicked = false;
            return "Regarder";
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
            new ClientGui().setVisible(true);
        });
    }
}
