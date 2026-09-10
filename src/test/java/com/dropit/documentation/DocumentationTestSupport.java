package com.dropit.documentation;

import com.dropit.global.exception.GlobalExceptionHandler;
import com.dropit.global.security.authentication.JwtAuthenticationToken;
import com.dropit.global.security.principal.AuthUser;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;

/** Common MockMvc wiring for isolated REST Docs contract tests. */
public abstract class DocumentationTestSupport {

    protected static final String DOCUMENTATION_ACCESS_TOKEN =
            "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidHlwZSI6IkFDQ0VTUyJ9.signature";

    protected MockMvc mockMvc;

    protected void configure(
            RestDocumentationContextProvider restDocumentation,
            Object controller,
            HandlerMethodArgumentResolver... additionalResolvers
    ) {
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();
        resolvers.add(new AuthenticationPrincipalArgumentResolver());
        resolvers.addAll(Arrays.asList(additionalResolvers));

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(resolvers.toArray(HandlerMethodArgumentResolver[]::new))
                .apply(documentationConfiguration(restDocumentation))
                .build();

        authenticate(1L);
    }

    protected void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(new AuthUser(userId), List.of())
        );
    }

    protected RequestPostProcessor bearerToken() {
        return request -> {
            request.addHeader("Authorization", DOCUMENTATION_ACCESS_TOKEN);
            return request;
        };
    }

    protected MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.with(bearerToken());
    }

    @AfterEach
    protected void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }
}
