package com.dropit.order.dto.response;

import java.util.UUID;

public record OrderPublicationErrorResponse(String code, String message, UUID requestId) {
}
