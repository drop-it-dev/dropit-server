package com.dropit.notification.email.sender;

import com.dropit.notification.email.messaging.EmailSqsProperties;
import io.awspring.cloud.autoconfigure.ses.SesClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class PurchaseEmailSesClientConfig {

    static final long MAX_API_CALL_TIMEOUT_MILLIS = 20_000;
    static final long MAX_API_CALL_ATTEMPT_TIMEOUT_MILLIS = 5_000;
    // Covers the three-attempt markSent path and the following manual acknowledgement.
    static final long POST_SEND_PROCESSING_BUDGET_MILLIS = 5_000;

    @Bean
    SesClientCustomizer purchaseEmailSesClientCustomizer(
            EmailSesProperties sesProperties,
            EmailSqsProperties sqsProperties
    ) {
        validateTimeouts(sesProperties, sqsProperties);

        return builder -> builder.overrideConfiguration(
                builder.overrideConfiguration().copy(configuration -> configuration
                        .apiCallTimeout(Duration.ofMillis(
                                sesProperties.apiCallTimeoutMillis()
                        ))
                        .apiCallAttemptTimeout(Duration.ofMillis(
                                sesProperties.apiCallAttemptTimeoutMillis()
                        )))
        );
    }

    private void validateTimeouts(
            EmailSesProperties sesProperties,
            EmailSqsProperties sqsProperties
    ) {
        long apiCallTimeoutMillis = sesProperties.apiCallTimeoutMillis();
        long attemptTimeoutMillis = sesProperties.apiCallAttemptTimeoutMillis();
        long visibilityTimeoutMillis = Math.multiplyExact(
                sqsProperties.consumerVisibilityTimeoutSeconds(),
                1_000L
        );

        requireRange(
                apiCallTimeoutMillis,
                1,
                MAX_API_CALL_TIMEOUT_MILLIS,
                "app.notification.email.ses.api-call-timeout-millis"
        );
        requireRange(
                attemptTimeoutMillis,
                1,
                MAX_API_CALL_ATTEMPT_TIMEOUT_MILLIS,
                "app.notification.email.ses.api-call-attempt-timeout-millis"
        );
        if (attemptTimeoutMillis > apiCallTimeoutMillis) {
            throw new IllegalArgumentException(
                    "app.notification.email.ses.api-call-attempt-timeout-millis "
                            + "must not exceed api-call-timeout-millis"
            );
        }
        long requiredVisibilityTimeoutMillis = Math.addExact(
                apiCallTimeoutMillis,
                POST_SEND_PROCESSING_BUDGET_MILLIS
        );
        if (requiredVisibilityTimeoutMillis >= visibilityTimeoutMillis) {
            throw new IllegalArgumentException(
                    "app.notification.email.sqs.consumer-visibility-timeout-seconds must leave "
                            + "time after the SES api-call-timeout-millis for markSent retries "
                            + "and manual acknowledgement"
            );
        }
    }

    private void requireRange(long value, long min, long max, String propertyName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    propertyName + " must be between " + min + " and " + max
            );
        }
    }
}
