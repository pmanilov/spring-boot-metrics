#!/bin/bash

reset_loss() {
    CONTAINER_NAME=$1

    CID=$(docker-compose ps -q $CONTAINER_NAME)
    if [ -z "$CID" ]; then return; fi

    IFLINK=$(docker exec $CID sh -c 'cat /sys/class/net/eth0/iflink' | tr -d '\r')
    VETH=$(ip link | grep -E "^$IFLINK:" | awk -F: '{print $2}' | cut -d'@' -f1 | tr -d ' ')

    if [ -n "$VETH" ]; then
        echo "Resetting settings for $CONTAINER_NAME ($VETH)..."
        tc qdisc del dev $VETH root 2>/dev/null
    fi
}

echo "=== Network Reset ==="
reset_loss "mosquitto"
reset_loss "metrics-server-mqtt-udp"
echo "All restrictions removed."