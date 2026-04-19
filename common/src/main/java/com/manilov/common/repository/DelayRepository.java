package com.manilov.common.repository;

import com.manilov.common.domain.Delay;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DelayRepository {
    private static final String INSERT_SQL =
            "INSERT INTO delays (ts, server_id, delay_ms) VALUES (?, ?, ?)";
    private static final int MAX_BATCH = 2000;

    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentLinkedQueue<Delay> buffer = new ConcurrentLinkedQueue<>();
    private final AtomicLong failedRows = new AtomicLong();

    public Double selectAverageDelay(String serverId) {
        return jdbcTemplate.queryForObject(
                "SELECT AVG(delay_ms) FROM delays WHERE server_id = ?", Double.class, serverId);
    }

    public void save(Delay delay) {
        buffer.add(delay);
    }

    @Scheduled(fixedDelay = 1000)
    public void flush() {
        drainAndWrite();
    }

    @PreDestroy
    public void flushOnShutdown() {
        drainAndWrite();
    }

    public void deleteAll(String serverId) {
        drainAndWrite();
        jdbcTemplate.update("ALTER TABLE delays DELETE WHERE server_id = ?", serverId);
    }

    public long getFailedRowCount() {
        return failedRows.get();
    }

    private synchronized void drainAndWrite() {
        while (!buffer.isEmpty()) {
            List<Delay> batch = new ArrayList<>(MAX_BATCH);
            for (int i = 0; i < MAX_BATCH; i++) {
                Delay delay = buffer.poll();
                if (delay == null) {
                    break;
                }
                batch.add(delay);
            }
            if (batch.isEmpty()) {
                return;
            }
            try {
                jdbcTemplate.batchUpdate(INSERT_SQL, batch, batch.size(), (ps, d) -> {
                    ps.setTimestamp(1, Timestamp.from(d.getTs()));
                    ps.setString(2, d.getServerId());
                    ps.setDouble(3, d.getDelayMs());
                });
            } catch (Exception e) {
                failedRows.addAndGet(batch.size());
                log.warn("Failed to flush delays batch of {}: {}", batch.size(), e.getMessage());
            }
        }
    }
}