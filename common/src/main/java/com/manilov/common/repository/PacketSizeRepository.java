package com.manilov.common.repository;

import com.manilov.common.domain.PacketSize;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PacketSizeRepository {
    private static final String INSERT_SQL =
            "INSERT INTO packet_sizes (ts, server_id, packet_size) VALUES (?, ?, ?)";
    private static final int MAX_BATCH = 10_000;
    private static final int MAX_BATCHES_PER_FLUSH = 4;

    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentLinkedDeque<PacketSize> buffer = new ConcurrentLinkedDeque<>();
    private final AtomicLong failedRows = new AtomicLong();

    public Double selectAveragePacketSize(String serverId) {
        return jdbcTemplate.queryForObject(
                "SELECT AVG(packet_size) FROM packet_sizes WHERE server_id = ?", Double.class, serverId);
    }

    public void save(PacketSize packetSize) {
        buffer.add(packetSize);
    }

    @Scheduled(fixedDelay = 2000)
    public void flush() {
        drainAndWrite();
    }

    @PreDestroy
    public void flushOnShutdown() {
        drainAndWrite();
    }

    public void deleteAll(String serverId) {
        drainAndWrite();
        jdbcTemplate.update("TRUNCATE TABLE packet_sizes");
    }

    public long getFailedRowCount() {
        return failedRows.get();
    }

    private synchronized void drainAndWrite() {
        for (int flushedBatches = 0; flushedBatches < MAX_BATCHES_PER_FLUSH; flushedBatches++) {
            List<PacketSize> batch = new ArrayList<>(MAX_BATCH);
            for (int i = 0; i < MAX_BATCH; i++) {
                PacketSize packetSize = buffer.pollFirst();
                if (packetSize == null) {
                    break;
                }
                batch.add(packetSize);
            }
            if (batch.isEmpty()) {
                return;
            }
            try {
                jdbcTemplate.batchUpdate(INSERT_SQL, batch, batch.size(), (ps, p) -> {
                    ps.setTimestamp(1, Timestamp.from(p.getTs()));
                    ps.setString(2, p.getServerId());
                    ps.setInt(3, p.getPacketSize());
                });
            } catch (Exception e) {
                failedRows.addAndGet(batch.size());
                requeueAtFront(batch);
                log.warn("Failed to flush packet_sizes batch of {} (buffer now {}): {}",
                        batch.size(), buffer.size(), e.getMessage());
                return;
            }
        }
    }

    private void requeueAtFront(List<PacketSize> batch) {
        for (int i = batch.size() - 1; i >= 0; i--) {
            buffer.addFirst(batch.get(i));
        }
    }
}
