package edu.usth.songsong.daemon;

import java.io.*;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

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
            String[] parts = line.trim().split("\\s+");
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
        if (parts.length < 4) {
            out.write("ERROR Usage: GET <filename> <offset> <length>\n".getBytes());
            out.flush();
            return;
        }

        String filename = parts[1];
        long offset = Long.parseLong(parts[2]);
        int length = Integer.parseInt(parts[3]);

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

            while (remaining > 0) {
                int toRead = Math.min(buffer.length, remaining);
                int bytesRead = raf.read(buffer, 0, toRead);
                if (bytesRead == -1)
                    break; // EOF
                out.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
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
