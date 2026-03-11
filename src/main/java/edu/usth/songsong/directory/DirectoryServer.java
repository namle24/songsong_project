package edu.usth.songsong.directory;

import edu.usth.songsong.common.ClientInfo;
import edu.usth.songsong.common.DirectoryService;

import java.io.IOException;
import java.net.Socket;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * RMI server that tracks which clients own which files.
 * Periodically pings clients to detect disconnections.
 */
public class DirectoryServer extends UnicastRemoteObject implements DirectoryService {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(DirectoryServer.class.getName());
    private static final int PING_TIMEOUT_MS = 3000;
    private static final int HEALTH_CHECK_INTERVAL_SEC = 30;

    // filename -> set of clients that have it
    private final ConcurrentHashMap<String, Set<ClientInfo>> fileToClientsMap;

    // all registered clients (for health check iteration)
    private final Set<ClientInfo> registeredClients;

    // client -> its files (reverse index for fast unregister)
    private final ConcurrentHashMap<ClientInfo, Set<String>> clientToFilesMap;

    private final ScheduledExecutorService healthChecker;

    public DirectoryServer() throws RemoteException {
        super(1099); // Use fixed port 1099 for the remote object to be firewall-friendly
        this.fileToClientsMap = new ConcurrentHashMap<>();
        this.registeredClients = Collections.synchronizedSet(new HashSet<>());
        this.clientToFilesMap = new ConcurrentHashMap<>();
        this.healthChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HealthChecker");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public synchronized void register(String ip, int port, List<String> files) throws RemoteException {
        ClientInfo client = new ClientInfo(ip, port);
        registeredClients.add(client);

        // track what this client owns
        Set<String> owned = clientToFilesMap.computeIfAbsent(client,
                k -> Collections.synchronizedSet(new HashSet<>()));
        owned.addAll(files);

        // add client to each file's source set
        for (String file : files) {
            fileToClientsMap.computeIfAbsent(file,
                    k -> Collections.synchronizedSet(new HashSet<>()))
                    .add(client);
        }

        LOG.info("Registered " + client + " with " + files.size() + " file(s): " + files);
    }

    @Override
    public synchronized void unregister(String ip, int port) throws RemoteException {
        ClientInfo client = new ClientInfo(ip, port);
        registeredClients.remove(client);

        Set<String> owned = clientToFilesMap.remove(client);
        if (owned == null) {
            LOG.warning("Unregister called for unknown client: " + client);
            return;
        }

        // remove client from each file's source set
        for (String file : owned) {
            Set<ClientInfo> clients = fileToClientsMap.get(file);
            if (clients != null) {
                clients.remove(client);
                if (clients.isEmpty()) {
                    fileToClientsMap.remove(file);
                }
            }
        }

        LOG.info("Unregistered " + client);
    }

    @Override
    public Set<ClientInfo> lookup(String filename) throws RemoteException {
        Set<ClientInfo> clients = fileToClientsMap.get(filename);
        if (clients == null || clients.isEmpty()) {
            LOG.info("Lookup '" + filename + "' -> no sources");
            return Collections.emptySet();
        }

        // defensive copy so caller can't mess with our internal set
        Set<ClientInfo> copy;
        synchronized (clients) {
            copy = new HashSet<>(clients);
        }
        LOG.info("Lookup '" + filename + "' -> " + copy.size() + " source(s): " + copy);
        return copy;
    }

    /** Starts periodic ping to detect dead clients. */
    public void startHealthCheck() {
        healthChecker.scheduleAtFixedRate(() -> {
            LOG.fine("Health check running...");

            List<ClientInfo> snapshot;
            synchronized (registeredClients) {
                snapshot = new ArrayList<>(registeredClients);
            }

            for (ClientInfo client : snapshot) {
                if (!isAlive(client)) {
                    LOG.warning("Client " + client + " unreachable, removing...");
                    try {
                        unregister(client.getIp(), client.getPort());
                    } catch (RemoteException e) {
                        LOG.log(Level.SEVERE, "Failed to unregister dead client: " + client, e);
                    }
                }
            }
        }, HEALTH_CHECK_INTERVAL_SEC, HEALTH_CHECK_INTERVAL_SEC, TimeUnit.SECONDS);

        LOG.info("Health checker started (every " + HEALTH_CHECK_INTERVAL_SEC + "s)");
    }

    /** Try TCP connect to check if Daemon is still alive. */
    private boolean isAlive(ClientInfo client) {
        try (Socket s = new Socket()) {
            s.connect(new java.net.InetSocketAddress(client.getIp(), client.getPort()), PING_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void main(String[] args) {
        try {
            DirectoryServer server = new DirectoryServer();

            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("DirectoryService", server);

            server.startHealthCheck();

            LOG.info("DirectoryServer bound in RMI registry on port 1099");
            LOG.info("Waiting for client registrations...");

        } catch (RemoteException e) {
            LOG.log(Level.SEVERE, "Failed to start DirectoryServer", e);
            System.exit(1);
        }
    }
}
