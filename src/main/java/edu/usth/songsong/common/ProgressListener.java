package edu.usth.songsong.common;

/**
 * Interface to monitor download progress and bind with the UI (Swing Server).
 * Allows real-time Progress Bar updates and logging to a TextArea.
 */
public interface ProgressListener {

    /**
     * Updated when data is pushed to the temporary file.
     * @param bytesRead Number of bytes successfully downloaded
     */
    void onProgress(int bytesRead);

    /**
     * Event triggered upon successful full download.
     * @param durationMs Execution time
     * @param path Output file path
     */
    void onComplete(long durationMs, String path);

    /**
     * Critical error event that causes file retrieval to fail.
     * @param message Description of the error
     */
    void onError(String message);

    /**
     * Logs plain text reports to the UI frame.
     * @param message Reporting message
     */
    void onLog(String message);
}
