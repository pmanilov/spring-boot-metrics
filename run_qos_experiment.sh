#!/bin/bash
# Запуск QoS-эксперимента (QosPacketLossExperimentRunner) на удалённой машине
# с 1 ГБ ОЗУ. Бюджет:
#   - heap (Xmx)               192 MB — ConcurrentHashMap'ы для InFlight, метаданные
#   - direct memory            384 MB — Netty/QUIC outbound queue под нагрузкой
#   - metaspace + stacks + JIT ~150 MB
#   - OS / shell / docker      остаток (~270 MB)
# Итого JVM ~730 MB. Если процессу не хватает direct memory (UnpooledByteBuf
# leaks или OOM:Direct), уменьшайте c/S/λ или поднимайте RAM на хосте —
# на 1 ГБ для c=15 × S=8192 × λ=1000 принципиально не хватит буфера.
#
# Использование (на удалённом хосте):
#   nohup ./run_qos_experiment.sh > qos_experiment.log 2>&1 &
#   tail -f qos_experiment.log
#
# Опциональные переменные окружения:
#   JAR             путь к fat-jar (default: experiment-packet-loss-qos1-1.2.jar)
#   DIRECT_MEM      MaxDirectMemorySize (default: 384m)
#   HEAP_MAX        -Xmx (default: 192m)

set -e

JAR="${JAR:-experiment-packet-loss-qos1-1.2.jar}"
DIRECT_MEM="${DIRECT_MEM:-384m}"
HEAP_MAX="${HEAP_MAX:-192m}"

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
