package com.dropit.ranking.batch;

import com.dropit.ranking.repository.SellerRankingExecutionStore;
import com.dropit.ranking.repository.SellerRankingSnapshotStore;
import com.dropit.ranking.repository.SellerSalesRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

@ImportAutoConfiguration({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
        DataRedisAutoConfiguration.class})
public class SellerRankingBatch {

    public static void run(String[] args) {
        try (var context = new SpringApplicationBuilder(SellerRankingBatch.class)
                .web(WebApplicationType.NONE).profiles("ranking").run(args)) {
            String asOf = context.getEnvironment().getProperty("app.ranking.as-of");
            context.getBean(SellerRankingJob.class).run(
                    asOf == null || asOf.isBlank() ? null : Instant.parse(asOf));
        }
    }

    @Bean
    SellerSalesRepository sellerSalesRepository(NamedParameterJdbcTemplate jdbc) {
        return new SellerSalesRepository(jdbc);
    }

    @Bean
    SellerRankingSnapshotStore sellerRankingSnapshotStore(StringRedisTemplate redis) {
        return new SellerRankingSnapshotStore(redis);
    }

    @Bean
    SellerRankingExecutionStore sellerRankingExecutionStore(
            StringRedisTemplate redis,
            @Value("${app.ranking.lock-ttl:65m}") Duration lockTtl) {
        return new SellerRankingExecutionStore(redis, lockTtl);
    }

    @Bean
    SellerRankingJob sellerRankingJob(SellerSalesRepository repository, SellerRankingSnapshotStore snapshots,
                                     SellerRankingExecutionStore executions,
                                     @Value("${app.ranking.order-time-zone}") String orderTimeZone) {
        return new SellerRankingJob(repository, snapshots, executions,
                Clock.systemUTC(), ZoneId.of(orderTimeZone));
    }
}
