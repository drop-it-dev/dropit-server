package com.dropit.notification.email.sender;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification.email.ses")
public record EmailSesProperties(
        String fromAddress,
        long apiCallTimeoutMillis,
        long apiCallAttemptTimeoutMillis
) {
}
