#!/bin/bash

# Script: run_multiple_daemons.sh
# Usage: ./run_multiple_daemons.sh <number_of_daemons> <server_ip>
# Example: ./run_multiple_daemons.sh 10 192.168.2.59

if [ "$#" -lt 2 ]; then
    echo "Usage: $0 <number_of_daemons> <server_ip>"
    echo "Example: $0 10 192.168.2.59"
    echo "Example: $0 50 100.71.169.126"
    exit 1
fi

NUM_DAEMONS=$1
SERVER_IP=$2
START_PORT=5000

echo " Starting $NUM_DAEMONS Daemons registering to $SERVER_IP..."

for i in $(seq 1 $NUM_DAEMONS); do
    CURRENT_PORT=$((START_PORT + i - 1))
    
    # Run in background (&) so we can start many at once
    java -cp target/classes edu.usth.songsong.daemon.DaemonServer localhost 1099 $CURRENT_PORT ./data $SERVER_IP > /dev/null 2>&1 &
    
    echo " Started Daemon $i on port $CURRENT_PORT"
done

echo ""
echo " All $NUM_DAEMONS daemons are running in the background!"
echo " To stop them all later, run: pkill -f DaemonServer"
