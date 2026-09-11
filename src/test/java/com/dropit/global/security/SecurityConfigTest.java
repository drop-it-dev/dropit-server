package com.dropit.global.security;

import com.dropit.global.security.jwt.JwtFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SecurityConfigTest.TestConfig.class)
@WebAppConfiguration
@TestPropertySource(properties = {
        "docs.auth.username=docs",
        "docs.auth.password=secret"
})
class SecurityConfigTest {

    @Autowired
    private SecurityConfig securityConfig;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void documentationPasswordIsStoredAsBcryptHash() {
        UserDetailsService userDetailsService = securityConfig.documentationUserDetailsService(
                "docs",
                "secret",
                passwordEncoder
        );

        UserDetails user = userDetailsService.loadUserByUsername("docs");

        assertTrue(passwordEncoder.matches("secret", user.getPassword()));
    }

    @Test
    void documentationCredentialsAreRequired() {
        SecurityConfig standaloneSecurityConfig = new SecurityConfig(null, null);

        assertThrows(
                IllegalStateException.class,
                () -> standaloneSecurityConfig.documentationUserDetailsService(
                        "",
                        "secret",
                        passwordEncoder
                )
        );
    }

    @Test
    void documentationWithoutCredentialsReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/openapi/test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void documentationWithValidCredentialsReturnsOk() throws Exception {
        mockMvc.perform(get("/openapi/test").with(httpBasic("docs", "secret")))
                .andExpect(status().isOk());
    }

    @Test
    void documentationWithInvalidCredentialsReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/openapi/test").with(httpBasic("docs", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestConfig {

        @Bean
        JwtFilter jwtFilter() {
            return mock(JwtFilter.class);
        }

        @Bean
        SecurityErrorResponseSender errorResponseSender() {
            return mock(SecurityErrorResponseSender.class);
        }

        @Bean
        DocumentationTestController documentationTestController() {
            return new DocumentationTestController();
        }
    }

    @RestController
    static class DocumentationTestController {

        @GetMapping("/openapi/test")
        void getDocumentation(HttpServletResponse response) throws Exception {
            response.setStatus(HttpServletResponse.SC_OK);
        }
    }
}
