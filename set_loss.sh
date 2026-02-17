#!/bin/bash

LOSS=$1
if [ -z "$LOSS" ]; then
    echo "Usage: sudo ./set_exact_loss.sh <percent>"
    exit 1
fi

WHITELIST_SERVICE_NAME="metrics-server-mqtt"

WHITELIST_CID=$(docker-compose ps -q $WHITELIST_SERVICE_NAME)

if [ -z "$WHITELIST_CID" ]; then
    echo "Error: Service '$WHITELIST_SERVICE_NAME' not found in docker-compose."
    exit 1
fi

WHITELIST_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' $WHITELIST_CID)

if [ -z "$WHITELIST_IP" ]; then
    echo "Error: Could not find IP address for container ID $WHITELIST_CID"
    exit 1
fi

echo "Whitelist IP (Metrics Server): $WHITELIST_IP"

apply_loss_logic() {
    TARGET_CONTAINER=$1

    echo "=== Configuring $TARGET_CONTAINER ==="

    CID=$(docker-compose ps -q $TARGET_CONTAINER)
    if [ -z "$CID" ]; then echo "Target container not found"; return; fi

    IFLINK=$(docker exec $CID sh -c 'cat /sys/class/net/eth0/iflink' | tr -d '\r')
    VETH=$(ip link | grep -E "^$IFLINK:" | awk -F: '{print $2}' | cut -d'@' -f1 | tr -d ' ')

    if [ -z "$VETH" ]; then echo "No veth found"; return; fi

    tc qdisc del dev $VETH root 2>/dev/null

    tc qdisc add dev $VETH root handle 1: prio bands 3 priomap 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1

    tc qdisc add dev $VETH parent 1:2 handle 20: netem loss ${LOSS}%

    tc qdisc add dev $VETH parent 1:1 handle 10: pfifo_fast

    tc filter add dev $VETH protocol ip parent 1:0 prio 1 u32 \
       match ip src $WHITELIST_IP \
       flowid 1:1

    tc filter add dev $VETH protocol ip parent 1:0 prio 1 u32 \
           match ip sport 8123 0xffff \
           flowid 1:1

    echo "Logic applied on $VETH:"
    echo "1. Traffic from $WHITELIST_IP -> CLEAN (Band 1:1)"
    echo "2. Everything else (including Gateway/JavaClient) -> LOSS ${LOSS}% (Band 1:2)"
}

apply_loss_logic "mosquitto"
apply_loss_logic "metrics-server-mqtt-udp"