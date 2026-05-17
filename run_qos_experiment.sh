#!/bin/bash
# Запуск QoS-эксперимента (QosPacketLossExperimentRunner) на удалённой машине
# с 2 ГБ ОЗУ (vm1674206, ~1.2 GB available при простое). Бюджет:
#   - heap (Xmx)               256 MB — ConcurrentHashMap'ы для InFlight, метаданные
#   - direct memory            768 MB — Netty/QUIC outbound queue при 15× publishers
#   - metaspace + stacks + JIT ~150 MB
#   - OS / shell / docker      остаток (~800 MB)
# Итого JVM ~1.2 GB. При c=15 × S=8192 × λ=1000 direct memory пика держится
# около 500-650 MB; запас в 100+ MB обязателен, иначе Netty pool блокируется
# и MqttQuicClient.WRITE_TIMEOUT (5s) валит публикации с
# "Failed while waiting for MQTT publish completion".
#
# Использование (на удалённом хосте):
#   nohup ./run_qos_experiment.sh > qos_experiment.log 2>&1 &
#   tail -f qos_experiment.log
#
# Опциональные переменные окружения:
#   JAR             путь к fat-jar (default: experiment-packet-loss-qos1-1.2.jar)
#   DIRECT_MEM      MaxDirectMemorySize (default: 768m)
#   HEAP_MAX        -Xmx (default: 256m)

set -e

JAR="${JAR:-experiment-packet-loss-qos1-1.2.jar}"
DIRECT_MEM="${DIRECT_MEM:-768m}"
HEAP_MAX="${HEAP_MAX:-256m}"

if [ ! -f "$JAR" ]; then
    echo "JAR not found: $JAR" >&2
    echo "Override via JAR=/path/to/experiment-packet-loss-qos1-*.jar $0" >&2
    exit 1
fi

exec java \
    -Xms128m \
    "-Xmx${HEAP_MAX}" \
    "-XX:MaxDirectMemorySize=${DIRECT_MEM}" \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=100 \
    -jar "$JAR"
