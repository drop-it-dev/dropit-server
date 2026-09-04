package com.dropit.global.security;

import com.dropit.global.exception.ErrorCode;
import com.dropit.global.exception.ErrorResponse;
import com.dropit.global.exception.ServiceException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class SecurityErrorResponseSender {

    private final ObjectMapper objectMapper;

    public void send(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.from(errorCode)));
    }

    public void send(HttpServletResponse response, ServiceException exception) throws IOException {
        send(response, exception.getErrorCode());
    }
}
