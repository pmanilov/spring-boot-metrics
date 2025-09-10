package com.manilov.common.configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class ClickHouseConfig {
    @Bean
    public DataSource clickHouseDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:clickhouse://clickhouse:8123/metrics");
        config.setUsername("default");
        config.setPassword("");
        config.setDriverClassName("com.clickhouse.jdbc.Driver");

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(60000);
        config.setConnectionTimeout(30000);

        return new HikariDataSource(config);
    }
}
