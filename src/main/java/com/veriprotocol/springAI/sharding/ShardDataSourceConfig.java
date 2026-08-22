package com.veriprotocol.springAI.sharding;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ShardDataSourceConfig {

    @Bean(name = "shard0DataSource")
    @Primary
    public DataSource shard0DataSource(
            @Value("${smartsearch.shards.shard-0.url}") String url,
            @Value("${smartsearch.shards.shard-0.username}") String username,
            @Value("${smartsearch.shards.shard-0.password}") String password) {

        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .build();
    }

    @Bean(name = "shard1DataSource")
    public DataSource shard1DataSource(
            @Value("${smartsearch.shards.shard-1.url}") String url,
            @Value("${smartsearch.shards.shard-1.username}") String username,
            @Value("${smartsearch.shards.shard-1.password}") String password) {

        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .build();
    }

    @Bean(name = "shard0JdbcTemplate")
    @Primary
    public JdbcTemplate shard0JdbcTemplate(
            @Qualifier("shard0DataSource") DataSource dataSource) {

        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "shard1JdbcTemplate")
    public JdbcTemplate shard1JdbcTemplate(
            @Qualifier("shard1DataSource") DataSource dataSource) {

        return new JdbcTemplate(dataSource);
    }


}