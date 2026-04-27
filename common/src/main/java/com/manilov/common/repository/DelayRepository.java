package com.manilov.common.repository;

import com.manilov.common.domain.Delay;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DelayRepository {
    private static final String INSERT_SQL =
            "INSERT INTO delays (ts, server_id, delay_ms) VALUES (?, ?, ?)";
    private static final int MAX_BATCH = 10_000;
    private static final int MAX_BATCHES_PER_FLUSH = 1;
    private static final int MAX_BUFFERED_ROWS = 50_000;
    private static final Duration FAILURE_BACKOFF = Duration.ofSeconds(30);

    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentLinkedDeque<Delay> buffer = new ConcurrentLinkedDeque<>();
    private final AtomicInteger bufferedRows = new AtomicInteger();
    private final AtomicLong failedRows = new AtomicLong();
    private volatile long nextFlushAttemptNanos;

    public void save(Delay delay) {
        buffer.addLast(delay);
        int size = bufferedRows.incrementAndGet();
        if (size > MAX_BUFFERED_ROWS) {
            Delay dropped = buffer.pollFirst();
            if (dropped != null) {
                bufferedRows.decrementAndGet();
                failedRows.incrementAndGet();
            }
        }
    }

    @Scheduled(fixedDelay = 10_000)
    public void flush() {
        drainAndWrite();
    }

    @PreDestroy
    public void flushOnShutdown() {
        drainAndWrite();
    }

    public void deleteAll(String serverId) {
        discardBufferedRows();
        jdbcTemplate.update("TRUNCATE TABLE delays");
    }

    public long getFailedRowCount() {
        return failedRows.get();
    }

    private synchronized void drainAndWrite() {
        long now = System.nanoTime();
        if (now < nextFlushAttemptNanos) {
            return;
        }
        for (int flushedBatches = 0; flushedBatches < MAX_BATCHES_PER_FLUSH; flushedBatches++) {
            List<Delay> batch = new ArrayList<>(MAX_BATCH);
            for (int i = 0; i < MAX_BATCH; i++) {
                Delay delay = buffer.pollFirst();
                if (delay == null) {
                    break;
                }
                bufferedRows.decrementAndGet();
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
                nextFlushAttemptNanos = System.nanoTime() + FAILURE_BACKOFF.toNanos();
                log.warn("Dropping delays batch of {} after ClickHouse failure; buffer now {}, next retry in {}s: {}",
                        batch.size(), bufferedRows.get(), FAILURE_BACKOFF.toSeconds(), e.getMessage());
                return;
            }
        }
    }

    private synchronized void discardBufferedRows() {
        int discarded = 0;
        while (buffer.pollFirst() != null) {
            discarded++;
        }
        if (discarded > 0) {
            int rows = discarded;
            bufferedRows.updateAndGet(current -> Math.max(0, current - rows));
            failedRows.addAndGet(discarded);
            log.warn("Discarded {} buffered delay rows before TRUNCATE", discarded);
        }
    }
}
