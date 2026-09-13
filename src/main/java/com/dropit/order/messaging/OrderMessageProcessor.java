package com.dropit.order.messaging;

import com.dropit.global.exception.ServiceException;
import com.dropit.order.service.OrderFinalizationService;
import com.dropit.order.service.OrderRequestFailureService;
import com.dropit.order.service.OrderRequestRedisSyncService;
import com.dropit.order.service.OrderRequestRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderMessageProcessor {

    private final OrderRequestRegistrationService registrationService;
    private final OrderFinalizationService finalizationService;
    private final OrderRequestFailureService failureService;
    private final OrderRequestRedisSyncService redisSyncService;

    public void process(OrderMessage message) {
        try {
            registrationService.register(message);
        } catch (DataIntegrityViolationException exception) {
            registrationService.verifyExisting(message);
        }

        try {
            finalizationService.finalizeOrder(message.requestId());
        } catch (ServiceException exception) {
            failureService.fail(message.requestId(), exception.getErrorCode().toString());
        }

        redisSyncService.sync(message.requestId());
    }
}
