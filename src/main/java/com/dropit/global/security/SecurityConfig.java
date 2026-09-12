package com.dropit.global.security;

import com.dropit.global.security.jwt.JwtErrorCode;
import com.dropit.global.security.jwt.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.util.StringUtils;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = true)
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final SecurityErrorResponseSender errorResponseSender;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService documentationUserDetailsService(
            @Value("${docs.auth.username:}") String username,
            @Value("${docs.auth.password:}") String password,
            PasswordEncoder passwordEncoder
    ) {
        validateDocumentationCredentials(username, password);

        return new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(passwordEncoder.encode(password))
                        .roles("DOCS")
                        .build()
        );
    }

    @Bean
    @Order(1)
    public SecurityFilterChain documentationSecurityFilterChain(
            HttpSecurity http,
            UserDetailsService documentationUserDetailsService
    ) throws Exception {
        return http
                .securityMatcher("/swagger-ui/**", "/openapi/**", "/webjars/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(Customizer.withDefaults())
                .logout(AbstractHttpConfigurer::disable)
                .userDetailsService(documentationUserDetailsService)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)          // 쿠키가 아닌 Authorization 헤더로 JWT를 전달하므로 CSRF 보호를 사용하지 않음
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .requestCache(AbstractHttpConfigurer::disable)  // 인증 전 요청을 세션에 저장한 뒤 복원하는 리다이렉트 흐름을 사용하지 않음
                .formLogin(AbstractHttpConfigurer::disable)     // 서버가 로그인 HTML 폼을 제공하는 방식을 사용하지 않음
                .httpBasic(AbstractHttpConfigurer::disable)     // 브라우저의 HTTP Basic 인증 팝업 방식을 사용하지 않음
                .logout(AbstractHttpConfigurer::disable)        // 서버 세션을 무효화하는 로그아웃 방식이 아닌 JWT 폐기 방식을 사용함
                .rememberMe(AbstractHttpConfigurer::disable)    // 자동 로그인을 위한 Remember-Me 쿠키를 발급하지 않음
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, cause) ->
                                errorResponseSender.send(response, JwtErrorCode.JWT_BEARER_TOKEN_REQUIRED))
                        .accessDeniedHandler((request, response, cause) ->
                                errorResponseSender.send(response, JwtErrorCode.JWT_ACCESS_DENIED))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/signup", "/auth/login", "/auth/reissue").permitAll()
                        .requestMatchers(HttpMethod.GET, "/drops", "/drops/*").permitAll()
                        .anyRequest().authenticated()
                )
                // JWT 인증이 익명 인증보다 먼저 처리되도록 순서 지정
                .addFilterBefore(jwtFilter, AnonymousAuthenticationFilter.class)
                .build();
    }

    private void validateDocumentationCredentials(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    "Swagger UI Basic Auth credentials are required. "
                            + "Set DOCS_AUTH_USERNAME and DOCS_AUTH_PASSWORD."
            );
        }
    }

    // JwtFilter가 Spring Security 체인에서만 실행되도록 자동 등록을 막는다. (서블릿 컨테이너 중복 등록 방지)
    @Bean
    public FilterRegistrationBean<JwtFilter> jwtFilterRegistration(JwtFilter filter) {
        FilterRegistrationBean<JwtFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
