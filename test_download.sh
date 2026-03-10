#!/bin/bash
set -e

cd /home/nam/SongSong/songsong_project
echo "Building project..."
mkdir -p out && find src/main/java -name "*.java" > sources.txt && javac -d out @sources.txt



echo "Starting DirectoryServer..."
java -cp out edu.usth.songsong.directory.DirectoryServer &
DIR_PID=$!
sleep 2

echo "Creating dummy file..."
mkdir -p data1 data2
head -c 5M </dev/urandom > data1/testfile.bin
cp data1/testfile.bin data2/testfile.bin

echo "Starting DaemonServer 1 on port 5000..."
java -cp out edu.usth.songsong.daemon.DaemonServer localhost 1099 5000 ./data1 &
D1_PID=$!
sleep 2

echo "Starting DaemonServer 2 on port 5001..."
java -cp out edu.usth.songsong.daemon.DaemonServer localhost 1099 5001 ./data2 &
D2_PID=$!
sleep 2

echo "Starting DownloadManager for testfile.bin (Parallel Mode)..."
# We expect this to use both daemons and succeed
java -cp out edu.usth.songsong.download.DownloadManager testfile.bin localhost 1099

echo "Verifying file integrity..."
# Test file should match original
if cmp -s data1/testfile.bin downloads/testfile.bin; then
    echo "SUCCESS: The downloaded file matches the original!"
else
    echo "FAIL: The downloaded file is corrupt or incomplete."
fi

# Cleanup
echo "Cleaning up processes..."
kill -9 $D2_PID $D1_PID $DIR_PID || true
rm -rf data1 data2 downloads sources.txt
