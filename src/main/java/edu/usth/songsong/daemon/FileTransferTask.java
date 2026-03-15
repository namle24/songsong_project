package edu.usth.songsong.daemon;

import java.io.*;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * Handles one TCP connection from a downloading client.
 * Protocol:
 * GET <filename> <offset> <length> -> sends raw bytes
 * SIZE <filename> -> sends file size as string
 */
public class FileTransferTask implements Runnable {

    private static final Logger LOG = Logger.getLogger(FileTransferTask.class.getName());

    private final Socket socket;
    private final File dataFolder;

    public FileTransferTask(Socket socket, File dataFolder) {
        this.socket = socket;
        this.dataFolder = dataFolder;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                OutputStream out = socket.getOutputStream()) {
            String line = in.readLine();
            if (line == null || line.isBlank()) {
                return;
            }

            LOG.fine("Request: " + line);
            String[] parts = line.trim().split("\\s+", 2); // limit=2: [command, rest]
            String command = parts[0].toUpperCase();

            switch (command) {
                case "GET" -> handleGet(parts, out);
                case "SIZE" -> handleSize(parts, out);
                default -> {
                    out.write(("ERROR Unknown command: " + command + "\n").getBytes());
                    out.flush();
                }
            }

        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error handling client request", e);
        } finally {
            closeSocket();
        }
    }

    /** GET <filename> <offset> <length> -> sends raw bytes from file. */
    private void handleGet(String[] parts, OutputStream out) throws IOException {
        // parts[1] là phần còn lại: "<filename> <offset> <length>"
        String[] subParts = parts[1].split("\\s+");
        int n = subParts.length;
        if (n < 3) {
            out.write("ERROR Usage: GET <filename> <offset> <length>\n".getBytes());
            out.flush();
            return;
        }
        // Ghép tên file: tất cả trừ 2 phần tử cuối (offset và length)
        long offset = Long.parseLong(subParts[n - 2]);
        int length = Integer.parseInt(subParts[n - 1]);
        String filename = String.join(" ", java.util.Arrays.copyOfRange(subParts, 0, n - 2));

        File file = new File(dataFolder, filename);
        if (!file.exists()) {
            out.write(("ERROR File not found: " + filename + "\n").getBytes());
            out.flush();
            return;
        }

        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            raf.seek(offset);

            byte[] buffer = new byte[8192];
            int remaining = length;

            int networkDelayMs = Integer.getInteger("songsong.network.delay", 0);

            // Data Compression: wrap output in GZip to reduce bytes on the wire
            GZIPOutputStream gzOut = new GZIPOutputStream(out);

            while (remaining > 0) {
                int toRead = Math.min(buffer.length, remaining);
                int bytesRead = raf.read(buffer, 0, toRead);
                if (bytesRead == -1)
                    break; // EOF
                gzOut.write(buffer, 0, bytesRead);
                remaining -= bytesRead;

                // Latency injection: simulate slow network for benchmarking
                if (networkDelayMs > 0) {
                    try { Thread.sleep(networkDelayMs); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            gzOut.finish(); // flush compressed data without closing underlying stream
            out.flush();

            LOG.fine("Sent " + (length - remaining) + " bytes of " + filename
                    + " (offset=" + offset + ")");
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** SIZE <filename> -> sends file size as text line. */
    private void handleSize(String[] parts, OutputStream out) throws IOException {
        if (parts.length < 2) {
            out.write("ERROR Usage: SIZE <filename>\n".getBytes());
            out.flush();
            return;
        }
        // parts[1] là toàn bộ tên file (kể cả khoảng trắng)
        String filename = parts[1];
        File file = new File(dataFolder, filename);

        if (!file.exists()) {
            out.write(("ERROR File not found: " + filename + "\n").getBytes());
            out.flush();
            return;
        }

        out.write((file.length() + "\n").getBytes());
        out.flush();
        LOG.fine("SIZE " + filename + " -> " + file.length());
    }

    private void closeSocket() {
        try {
            if (!socket.isClosed())
                socket.close();
        } catch (IOException ignored) {
        }
    }
}
