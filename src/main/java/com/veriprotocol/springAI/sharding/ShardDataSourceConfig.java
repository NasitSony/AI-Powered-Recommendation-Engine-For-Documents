package com.veriprotocol.springAI.sharding;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class ShardDataSourceConfig {

    private DataSource build(
            String url,
            String username,
            String password) {

        HikariDataSource ds = new HikariDataSource();

        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);

        ds.setConnectionTimeout(2000);
        ds.setValidationTimeout(1000);
        ds.setInitializationFailTimeout(-1);

        ds.setMaximumPoolSize(5);

        return ds;
    }

    // ---------- SHARD 0 / NODE A ----------

    @Bean(name = "shard0NodeADataSource")
    @Primary
    public DataSource shard0NodeADataSource(
            @Value("${smartsearch.shards.shard-0.node-a.url}") String url,
            @Value("${smartsearch.shards.shard-0.node-a.username}") String username,
            @Value("${smartsearch.shards.shard-0.node-a.password}") String password) {

        return build(url, username, password);
    }

    @Bean(name = "shard0NodeAJdbcTemplate")
    @Primary
    public JdbcTemplate shard0NodeAJdbcTemplate(
            @Qualifier("shard0NodeADataSource") DataSource dataSource) {

        return new JdbcTemplate(dataSource);
    }

    // ---------- SHARD 0 / NODE B ----------

    @Bean(name = "shard0NodeBDataSource")
    public DataSource shard0NodeBDataSource(
            @Value("${smartsearch.shards.shard-0.node-b.url}") String url,
            @Value("${smartsearch.shards.shard-0.node-b.username}") String username,
            @Value("${smartsearch.shards.shard-0.node-b.password}") String password) {

        return build(url, username, password);
    }

    @Bean(name = "shard0NodeBJdbcTemplate")
    public JdbcTemplate shard0NodeBJdbcTemplate(
            @Qualifier("shard0NodeBDataSource") DataSource dataSource) {

        return new JdbcTemplate(dataSource);
    }

    // ---------- SHARD 1 / NODE A ----------

    @Bean(name = "shard1NodeADataSource")
    public DataSource shard1NodeADataSource(
            @Value("${smartsearch.shards.shard-1.node-a.url}") String url,
            @Value("${smartsearch.shards.shard-1.node-a.username}") String username,
            @Value("${smartsearch.shards.shard-1.node-a.password}") String password) {

        return build(url, username, password);
    }

    @Bean(name = "shard1NodeAJdbcTemplate")
    public JdbcTemplate shard1NodeAJdbcTemplate(
            @Qualifier("shard1NodeADataSource") DataSource dataSource) {

        return new JdbcTemplate(dataSource);
    }

    // ---------- SHARD 1 / NODE B ----------

    @Bean(name = "shard1NodeBDataSource")
    public DataSource shard1NodeBDataSource(
            @Value("${smartsearch.shards.shard-1.node-b.url}") String url,
            @Value("${smartsearch.shards.shard-1.node-b.username}") String username,
            @Value("${smartsearch.shards.shard-1.node-b.password}") String password) {

        return build(url, username, password);
    }

    @Bean(name = "shard1NodeBJdbcTemplate")
    public JdbcTemplate shard1NodeBJdbcTemplate(
            @Qualifier("shard1NodeBDataSource") DataSource dataSource) {

        return new JdbcTemplate(dataSource);
    }
}