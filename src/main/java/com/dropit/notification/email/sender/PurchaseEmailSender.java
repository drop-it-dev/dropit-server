package com.dropit.notification.email.sender;

import com.dropit.notification.email.messaging.PurchaseCompletedMessage;

public interface PurchaseEmailSender {

    String send(PurchaseCompletedMessage message);
}
