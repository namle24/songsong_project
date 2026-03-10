package edu.usth.songsong.daemon;

import edu.usth.songsong.common.DirectoryService;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Daemon: runs on each client node.
 * Registers local files with Directory (RMI), then serves file fragments over
 * TCP.
 */
public class DaemonServer {

    private static final Logger LOG = Logger.getLogger(DaemonServer.class.getName());
    private static final String DATA_DIR = "./data";
    private static final int TCP_PORT = 5000;
    private static final int THREAD_POOL_SIZE = 10;

    private final String directoryHost;
    private final int directoryPort;
    private final File dataFolder;
    private final ExecutorService threadPool;

    public DaemonServer(String directoryHost, int directoryPort) {
        this.directoryHost = directoryHost;
        this.directoryPort = directoryPort;
        this.dataFolder = new File(DATA_DIR);
        this.threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    /** Scans data folder and returns list of filenames. */
    private List<String> scanFiles() {
        List<String> files = new ArrayList<>();
        File[] contents = dataFolder.listFiles();
        if (contents == null) {
            LOG.warning("Data folder not found or empty: " + dataFolder.getAbsolutePath());
            return files;
        }
        for (File f : contents) {
            if (f.isFile()) {
                files.add(f.getName());
            }
        }
        LOG.info("Scanned " + files.size() + " file(s) in " + dataFolder.getAbsolutePath());
        return files;
    }

    /** Registers this daemon's files with the Directory via RMI. */
    private void registerWithDirectory(List<String> files) {
        try {
            Registry registry = LocateRegistry.getRegistry(directoryHost, directoryPort);
            DirectoryService directory = (DirectoryService) registry.lookup("DirectoryService");

            String localIp = InetAddress.getLocalHost().getHostAddress();
            directory.register(localIp, TCP_PORT, files);

            LOG.info("Registered with Directory at " + directoryHost + ":" + directoryPort);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to register with Directory", e);
            System.exit(1);
        }
    }

    /** Adds shutdown hook to unregister from Directory on exit. */
    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Registry registry = LocateRegistry.getRegistry(directoryHost, directoryPort);
                DirectoryService directory = (DirectoryService) registry.lookup("DirectoryService");
                String localIp = InetAddress.getLocalHost().getHostAddress();
                directory.unregister(localIp, TCP_PORT);
                LOG.info("Unregistered from Directory");
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to unregister on shutdown", e);
            }
            threadPool.shutdownNow();
        }));
    }

    /** Starts TCP server, accepts connections and dispatches to thread pool. */
    private void startTcpServer() {
        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            LOG.info("Daemon TCP server listening on port " + TCP_PORT);

            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                LOG.fine("Accepted connection from " + clientSocket.getRemoteSocketAddress());
                threadPool.submit(new FileTransferTask(clientSocket, dataFolder));
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "TCP server error", e);
        }
    }

    /**
     * Usage: java DaemonServer [directoryHost] [directoryRmiPort]
     * Defaults: localhost 1099
     */
    public static void main(String[] args) {
        String dirHost = args.length > 0 ? args[0] : "localhost";
        int dirPort = args.length > 1 ? Integer.parseInt(args[1]) : 1099;

        DaemonServer daemon = new DaemonServer(dirHost, dirPort);

        List<String> files = daemon.scanFiles();
        if (files.isEmpty()) {
            LOG.warning("No files to serve. Put files in " + DATA_DIR);
        }

        daemon.registerWithDirectory(files);
        daemon.addShutdownHook();
        daemon.startTcpServer();
    }
}
