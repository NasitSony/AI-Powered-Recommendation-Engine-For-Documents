package com.veriprotocol.springAI.sharding;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ShardFlywayConfig {

    @Bean(name = "shard0Flyway")
    public Flyway shard0Flyway(
            @Qualifier("shard0NodeADataSource") DataSource nodeA,
            @Qualifier("shard0NodeBDataSource") DataSource nodeB) {

        DataSource writable =
                resolveWritable(nodeA, nodeB, "shard-0");

        return migrate(writable);
    }

    @Bean(name = "shard1Flyway")
    public Flyway shard1Flyway(
            @Qualifier("shard1NodeADataSource") DataSource nodeA,
            @Qualifier("shard1NodeBDataSource") DataSource nodeB) {

        DataSource writable =
                resolveWritable(nodeA, nodeB, "shard-1");

        return migrate(writable);
    }

    private DataSource resolveWritable(
            DataSource nodeA,
            DataSource nodeB,
            String shardName) {

        if (isWritable(nodeA)) {
            return nodeA;
        }

        if (isWritable(nodeB)) {
            return nodeB;
        }

        throw new IllegalStateException(
                "No writable primary available for " + shardName
        );
    }

    private boolean isWritable(DataSource dataSource) {

        try {
            JdbcTemplate jdbc =
                    new JdbcTemplate(dataSource);

            Boolean inRecovery =
                    jdbc.queryForObject(
                            "SELECT pg_is_in_recovery()",
                            Boolean.class
                    );

            return Boolean.FALSE.equals(inRecovery);

        } catch (Exception ex) {
            return false;
        }
    }

    private Flyway migrate(DataSource dataSource) {

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