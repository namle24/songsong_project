package edu.usth.songsong.download;

import edu.usth.songsong.common.ClientInfo;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Worker thread that downloads fragments from the queue in parallel.
 * Handles failover by pushing incomplete fragments back to the queue.
 */
public class DownloadWorker implements Callable<Void> {

    private static final Logger LOG = Logger.getLogger(DownloadWorker.class.getName());

    private final Queue<FragmentInfo> fragmentQueue;
    private final List<ClientInfo> availableDaemons;
    private final File destinationFile;

    public DownloadWorker(Queue<FragmentInfo> fragmentQueue, List<ClientInfo> availableDaemons, File destinationFile) {
        this.fragmentQueue = fragmentQueue;
        this.availableDaemons = availableDaemons;
        this.destinationFile = destinationFile;
    }

    @Override
    public Void call() {
        int daemonIndex = (int) (Thread.currentThread().getId() % availableDaemons.size());

        while (!Thread.currentThread().isInterrupted()) {
            FragmentInfo fragment = fragmentQueue.poll();
            if (fragment == null) {
                // Queue is empty, downloading is finished
                break;
            }

            // Select a daemon
            ClientInfo daemon = availableDaemons.get(daemonIndex);
            daemonIndex = (daemonIndex + 1) % availableDaemons.size();

            boolean success = downloadFragment(fragment, daemon);

            if (!success) {
                // Failover handling logic has put the remaining fragment back in queue.
                // We'll loop around and pick up whatever is next in queue.
                LOG.warning("Fragment failed. Attempting next available fragment from a different daemon.");
            }
        }

        return null;
    }

    private boolean downloadFragment(FragmentInfo fragment, ClientInfo daemon) {
        int downloadedBytes = 0;
        try (Socket socket = new Socket()) {
            // Set 2 seconds connection timeout to detect dead sources quickly
            socket.connect(new java.net.InetSocketAddress(daemon.getIp(), daemon.getPort()), 2000);
            socket.setSoTimeout(10000); // 10 seconds read timeout

            OutputStream out = socket.getOutputStream();
            // Protocol: GET <filename> <offset> <length>
            String request = String.format("GET %s %d %d\n", fragment.filename(), fragment.offset(), fragment.length());
            out.write(request.getBytes());
            out.flush();

            try (RandomAccessFile raf = new RandomAccessFile(destinationFile, "rw")) {
                raf.seek(fragment.offset());

                byte[] buffer = new byte[8192];
                int remaining = fragment.length();

                var inStream = socket.getInputStream();

                while (remaining > 0) {
                    int toRead = Math.min(buffer.length, remaining);
                    int readCount = inStream.read(buffer, 0, toRead);

                    if (readCount == -1) {
                        throw new IOException("Unexpected end of stream from daemon " + daemon);
                    }

                    raf.write(buffer, 0, readCount);
                    downloadedBytes += readCount;
                    remaining -= readCount;
                }
            }

            LOG.fine(String.format("Successfully downloaded fragment [%d - %d] from %s",
                    fragment.offset(), fragment.offset() + fragment.length() - 1, daemon));
            return true;

        } catch (SocketException | SocketTimeoutException e) {
            handleFailure(fragment, daemon, downloadedBytes, e);
            return false;
        } catch (IOException e) {
            handleFailure(fragment, daemon, downloadedBytes, e);
            return false;
        }
    }

    private void handleFailure(FragmentInfo fragment, ClientInfo daemon, int downloadedBytes, Exception e) {
        LOG.log(Level.WARNING,
                String.format(
                        "Connection to %s failed during transfer of %s. Bytes downloaded: %d out of %d. Reason: %s",
                        daemon, fragment.filename(), downloadedBytes, fragment.length(), e.getMessage()));

        int remainingBytes = fragment.length() - downloadedBytes;
        if (remainingBytes > 0) {
            long newOffset = fragment.offset() + downloadedBytes;
            FragmentInfo remainingFragment = new FragmentInfo(fragment.filename(), newOffset, remainingBytes);
            fragmentQueue.offer(remainingFragment);
            LOG.info(String.format("Put remaining fragment [%d, len=%d] back into the queue for failover.", newOffset,
                    remainingBytes));
        }
    }
}
