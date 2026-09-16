package com.dropit.notification.email.sender;

public record PurchaseEmailContent(
        String subject,
        String textBody,
        String htmlBody
) {
}
