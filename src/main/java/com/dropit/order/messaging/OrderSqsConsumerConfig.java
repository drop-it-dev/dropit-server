package com.dropit.order.messaging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class OrderSqsConsumerConfig {

    @Bean(destroyMethod = "shutdown")
    ThreadPoolExecutor sqsOrderConsumerExecutor(SqsProperties properties) {
        if (properties.consumerConcurrency() <= 0) {
            throw new IllegalArgumentException("app.order.sqs.consumer-concurrency must be positive");
        }

        return new ThreadPoolExecutor(
                properties.consumerConcurrency(),
                properties.consumerConcurrency(),
                0L,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
