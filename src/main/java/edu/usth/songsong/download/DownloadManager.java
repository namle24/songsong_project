package edu.usth.songsong.download;

import edu.usth.songsong.common.ClientInfo;
import edu.usth.songsong.common.DirectoryService;
import edu.usth.songsong.common.ProgressListener; // Added

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the parallel download process of a file.
 * Contacts the Directory to find daemons, calculates fragments,
 * pre-allocates file and orchestrates DownloadWorkers.
 */
public class DownloadManager {

    private static final Logger LOG = Logger.getLogger(DownloadManager.class.getName());
    private static final int FRAGMENT_SIZE = 1024 * 1024; // 1 MB fragments
    private static final String DOWNLOADS_DIR = "./downloads";

    private final String directoryHost;
    private final int directoryPort;

    public DownloadManager(String directoryHost, int directoryPort) {
        this.directoryHost = directoryHost;
        this.directoryPort = directoryPort;
        File dir = new File(DOWNLOADS_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public void downloadFile(String filename) {
        downloadFile(filename, null);
    }

    public void downloadFile(String filename, ProgressListener listener) {
        if (listener != null) listener.onLog("Starting download for: " + filename);
        LOG.info("Starting download for: " + filename);
        try {
            // Step 1: Call RMI lookup(filename)
            Registry registry = LocateRegistry.getRegistry(directoryHost, directoryPort);
            DirectoryService directory = (DirectoryService) registry.lookup("DirectoryService");
            Set<ClientInfo> daemons = directory.lookup(filename);

            if (daemons == null || daemons.isEmpty()) {
                String error = "File '" + filename + "' is not available on any connected client.";
                LOG.warning(error);
                if (listener != null) listener.onError(error);
                return;
            }
            
            List<ClientInfo> daemonList = new ArrayList<>(daemons);
            String infoMsg = "Found " + daemonList.size() + " daemons hosting '" + filename + "'.";
            LOG.info(infoMsg);
            if (listener != null) listener.onLog(infoMsg);

            // Step 2: Connect via TCP to *one* of the daemons and send SIZE <filename>
            long totalSize = getFileSizeFromDaemon(daemonList.get(0), filename);
            if (totalSize <= 0) {
                String error = "Failed to retrieve file size or file is empty.";
                LOG.warning(error);
                if (listener != null) listener.onError(error);
                return;
            }
            String sizeMsg = "Total file size: " + totalSize + " bytes.";
            LOG.info(sizeMsg);
            if (listener != null) listener.onLog(sizeMsg);

            // Output file
            File outputFile = new File(DOWNLOADS_DIR, filename);

            // Pre-allocate space
            try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
                raf.setLength(totalSize);
            }

            // Step 3 (Fragmentation)
            ConcurrentLinkedQueue<FragmentInfo> queue = new ConcurrentLinkedQueue<>();
            long offset = 0;
            while (offset < totalSize) {
                int length = (int) Math.min(FRAGMENT_SIZE, totalSize - offset);
                queue.offer(new FragmentInfo(filename, offset, length));
                offset += length;
            }
            String fragMsg = "Divided file into " + queue.size() + " fragments of basic size " + FRAGMENT_SIZE + " bytes.";
            LOG.info(fragMsg);
            if(listener != null) listener.onLog(fragMsg);

            // Step 4 (Parallel Execution)
            int threadCount = Math.max(1, daemonList.size()); // At least 1 thread, max depends on needs
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            String startMsg = "Starting " + threadCount + " download workers...";
            LOG.info(startMsg);
            if (listener != null) listener.onLog(startMsg);
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < threadCount; i++) {
                executor.submit(new DownloadWorker(queue, daemonList, outputFile, listener));
            }

            executor.shutdown();
            boolean finished = executor.awaitTermination(1, TimeUnit.HOURS);

            if (finished) {
                long duration = System.currentTimeMillis() - startTime;
                String successMsg = String.format("Download completed successfully! File saved to %s. Duration: %d ms.", outputFile.getAbsolutePath(), duration);
                LOG.info(successMsg);
                if (listener != null) {
                    listener.onLog(successMsg);
                    listener.onComplete(duration, outputFile.getAbsolutePath());
                }
            } else {
                String timeoutMsg = "Download timed out.";
                LOG.warning(timeoutMsg);
                if (listener != null) listener.onError(timeoutMsg);
            }

        } catch (Exception e) {
            String crashMsg = "Download manager encountered a critical error: " + e.getMessage();
            LOG.log(Level.SEVERE, crashMsg, e);
            if (listener != null) listener.onError(crashMsg);
        }
    }

    private long getFileSizeFromDaemon(ClientInfo daemon, String filename) {
        try (Socket socket = new Socket(daemon.getIp(), daemon.getPort());
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             OutputStream out = socket.getOutputStream()) {

            socket.setSoTimeout(20000); // 20 sec timeout for getting size over Internet

            String request = "SIZE " + filename + "\n";
            out.write(request.getBytes());
            out.flush();

            String response = in.readLine();
            if (response != null && !response.startsWith("ERROR")) {
                return Long.parseLong(response.trim());
            } else {
                LOG.warning("Daemon returned an error or null for SIZE request: " + response);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to get SIZE from daemon " + daemon, e);
        } catch (NumberFormatException e) {
            LOG.log(Level.WARNING, "Invalid size response from daemon", e);
        }
        return -1;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java edu.usth.songsong.download.DownloadManager <filename> [directoryHost] [directoryPort]");
            return;
        }

        String filename = args[0];
        String dirHost = args.length > 1 ? args[1] : "localhost";
        int dirPort = args.length > 2 ? Integer.parseInt(args[2]) : 1099;

        DownloadManager manager = new DownloadManager(dirHost, dirPort);
        manager.downloadFile(filename);
    }
}
