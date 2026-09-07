package com.dropit;

import com.dropit.global.security.jwt.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@EnableConfigurationProperties(JwtProperties.class)
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DropitServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DropitServerApplication.class, args);
    }

}
