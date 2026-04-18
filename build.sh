#!/bin/bash

./gradlew :server-mqtt:build
./gradlew :server-mqtt-udp:build
#./gradlew :server-mqtt-quic:build