package com.manilov.common.configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class ClickHouseConfig {
    @Value("${clickhouse.jdbc-url:jdbc:clickhouse://clickhouse:8123/metrics}")
    private String jdbcUrl;

    @Bean
    public DataSource clickHouseDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername("default");
        config.setPassword("");
        config.setDriverClassName("com.clickhouse.jdbc.Driver");

        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setIdleTimeout(60000);
        config.setConnectionTimeout(30000);
        config.setValidationTimeout(5000);
        config.setKeepaliveTime(30000);

        return new HikariDataSource(config);
    }
}
