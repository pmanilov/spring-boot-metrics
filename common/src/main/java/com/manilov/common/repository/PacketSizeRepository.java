package com.manilov.common.repository;

import com.manilov.common.domain.PacketSize;
import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
@AllArgsConstructor
public class PacketSizeRepository {
    private final JdbcTemplate jdbcTemplate;

    public Double selectAveragePacketSize(String serverId) {
        return jdbcTemplate.queryForObject("SELECT AVG(packet_size) FROM packet_sizes WHERE server_id = ?", Double.class, serverId);
    }

    public void save(PacketSize packetSize) {
        jdbcTemplate.update(
                "INSERT INTO packet_sizes (ts, server_id, packet_size) VALUES (?, ?, ?)",
                Timestamp.from(packetSize.getTs()),
                packetSize.getServerId(),
                packetSize.getPacketSize()
        );
    }

    public void deleteAll(String serverId) {
        jdbcTemplate.update("ALTER TABLE packet_sizes DELETE WHERE server_id = ?", serverId);
    }
}
