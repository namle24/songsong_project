package edu.usth.songsong.download;

/**
 * Represents a fragment of a file to be downloaded, keeping track of retry attempts.
 */
public record FragmentInfo(String filename, long offset, int length) {
}
