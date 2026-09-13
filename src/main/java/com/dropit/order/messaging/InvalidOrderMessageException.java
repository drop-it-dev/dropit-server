package com.dropit.order.messaging;

public class InvalidOrderMessageException extends RuntimeException {

    public InvalidOrderMessageException(String message) {
        super(message);
    }
}
