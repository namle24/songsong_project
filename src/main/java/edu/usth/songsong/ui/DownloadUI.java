package edu.usth.songsong.ui;

import edu.usth.songsong.common.ProgressListener;
import edu.usth.songsong.download.DownloadManager;

import javax.swing.*;
import java.awt.*;

public class DownloadUI extends JFrame implements ProgressListener {

    private JTextField fileInput;
    private JButton downloadBtn;
    private JProgressBar progressBar;
    private JTextArea logArea;

    private final String directoryHost;
    private final int directoryPort;
    private long totalFileSize = 0;
    private long downloadedBytes = 0;

    public DownloadUI(String directoryHost, int directoryPort) {
        this.directoryHost = directoryHost;
        this.directoryPort = directoryPort;

        setTitle("SongSong Parallel Downloader");
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Center on screen

        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        // Top Panel: Input and Button
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Filename:"));
        fileInput = new JTextField(20);
        topPanel.add(fileInput);

        downloadBtn = new JButton("Download");
        downloadBtn.addActionListener(e -> startDownload());
        topPanel.add(downloadBtn);

        add(topPanel, BorderLayout.NORTH);

        // Center Panel: Logs
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Download Logs"));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom Panel: Progress Bar
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        bottomPanel.add(progressBar, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void startDownload() {
        String filename = fileInput.getText().trim();
        if (filename.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a filename.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Reset UI state
        downloadBtn.setEnabled(false);
        fileInput.setEnabled(false);
        progressBar.setValue(0);
        logArea.setText("");
        totalFileSize = 0;
        downloadedBytes = 0;

        // Run download in a background thread to keep UI responsive
        Thread downloadThread = new Thread(() -> {
            try {
                DownloadManager manager = new DownloadManager(directoryHost, directoryPort);
                manager.downloadFile(filename, this);
            } finally {
                // Re-enable UI
                SwingUtilities.invokeLater(() -> {
                    downloadBtn.setEnabled(true);
                    fileInput.setEnabled(true);
                });
            }
        });
        downloadThread.start();
    }

    // --- ProgressListener Implementation ---

    @Override
    public void onLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            // Auto-scroll to bottom
            logArea.setCaretPosition(logArea.getDocument().getLength());
            
            // Extract file size from log if available (hacky but works without changing too much backend)
            if (message.startsWith("Total file size: ")) {
                 try {
                     String sizeStr = message.replace("Total file size: ", "").replace(" bytes.", "").trim();
                     totalFileSize = Long.parseLong(sizeStr);
                     progressBar.setMaximum(100); // We'll compute percentage manually
                 } catch (NumberFormatException ignored) {}
            }
        });
    }

    @Override
    public void onProgress(int bytesRead) {
        SwingUtilities.invokeLater(() -> {
            downloadedBytes += bytesRead;
            if (totalFileSize > 0) {
                int percent = (int) ((downloadedBytes * 100) / totalFileSize);
                progressBar.setValue(percent);
                progressBar.setString(percent + "% (" + (downloadedBytes / 1024) + " KB / " + (totalFileSize / 1024) + " KB)");
            } else {
                 progressBar.setIndeterminate(true);
                 progressBar.setString((downloadedBytes / 1024) + " KB downloaded");
            }
        });
    }

    @Override
    public void onComplete(long durationMs, String path) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setValue(100);
            progressBar.setString("100% - Done!");
            JOptionPane.showMessageDialog(this, 
                "Download completed in " + durationMs + " ms.\nSaved to: " + path, 
                "Success", 
                JOptionPane.INFORMATION_MESSAGE);
        });
    }

    @Override
    public void onError(String message) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(false);
            JOptionPane.showMessageDialog(this, 
                "Download Failed: " + message, 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        String dirHost = args.length > 0 ? args[0] : "localhost";
        int dirPort = args.length > 1 ? Integer.parseInt(args[1]) : 1099;

        SwingUtilities.invokeLater(() -> {
            DownloadUI ui = new DownloadUI(dirHost, dirPort);
            ui.setVisible(true);
        });
    }
}
