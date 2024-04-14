#!/bin/bash

./gradlew :server:build
./gradlew :server-mqtt:build
./gradlew :server-amqp:build
./gradlew :server-coap:build