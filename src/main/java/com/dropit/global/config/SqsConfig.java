package com.dropit.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class SqsConfig {

    @Bean
    SqsClient sqsClient(AwsRegionProvider regionProvider) {
        return SqsClient.builder()
                .region(regionProvider.getRegion())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }
}
