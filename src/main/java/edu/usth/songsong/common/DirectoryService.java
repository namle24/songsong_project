package edu.usth.songsong.common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Set;

/**
 * RMI interface for the central Directory service.
 */
public interface DirectoryService extends Remote {

    /** Daemon calls this on startup to register its files. */
    void register(String ip, int port, List<String> files) throws RemoteException;

    /** Daemon calls this on shutdown to remove itself. */
    void unregister(String ip, int port) throws RemoteException;

    /** Download calls this to find which clients have a file. */
    Set<ClientInfo> lookup(String filename) throws RemoteException;
}
