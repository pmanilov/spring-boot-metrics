package com.manilov.common.repository;

import com.manilov.common.domain.Delay;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DelayRepository {
    private final JdbcTemplate jdbcTemplate;

    public void save(Delay delay) {
        jdbcTemplate.update(
                "INSERT INTO delays (ts, server_id, delay_ms) VALUES (?, ?, ?)",
                delay.getTs(),
                delay.getServerId(),
                delay.getDelayMs()
        );
    }

    public void deleteAll(String serverId) {
        jdbcTemplate.update("ALTER TABLE delays DELETE WHERE server_id = ?", serverId);
    }
}
