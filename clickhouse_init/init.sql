CREATE DATABASE IF NOT EXISTS metrics;

CREATE TABLE IF NOT EXISTS metrics.delays
(
    ts DateTime64(9, 'Europe/Moscow'),
    server_id String,
    delay_ms Float64
)
ENGINE = MergeTree()
PARTITION BY tuple()
ORDER BY ts
TTL toDateTime(ts) + INTERVAL 1 HOUR DELETE
SETTINGS ttl_only_drop_parts = 1;

CREATE TABLE IF NOT EXISTS metrics.packet_sizes
(
    ts DateTime64(9, 'Europe/Moscow'),
    server_id String,
    packet_size Int32
)
ENGINE = MergeTree()
PARTITION BY tuple()
ORDER BY ts
TTL toDateTime(ts) + INTERVAL 1 HOUR DELETE
SETTINGS ttl_only_drop_parts = 1;
