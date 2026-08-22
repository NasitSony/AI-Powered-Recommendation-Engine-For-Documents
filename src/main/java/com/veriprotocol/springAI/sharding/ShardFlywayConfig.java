package com.veriprotocol.springAI.sharding;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShardFlywayConfig {

    @Bean(name = "shard0Flyway")
    public Flyway shard0Flyway(
            @Qualifier("shard0DataSource") DataSource dataSource) {

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();

        flyway.migrate();

        return flyway;
    }

    @Bean(name = "shard1Flyway")
    public Flyway shard1Flyway(
            @Qualifier("shard1DataSource") DataSource dataSource) {

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();

        flyway.migrate();

        return flyway;
    }
}