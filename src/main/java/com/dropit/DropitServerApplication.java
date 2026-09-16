package com.dropit;

import com.dropit.global.security.jwt.JwtProperties;
import com.dropit.notification.email.messaging.EmailSqsProperties;
import com.dropit.notification.email.sender.EmailSesProperties;
import com.dropit.order.messaging.SqsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableJpaAuditing
@EnableScheduling
@EnableConfigurationProperties({
        JwtProperties.class,
        SqsProperties.class,
        EmailSqsProperties.class,
        EmailSesProperties.class
})
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DropitServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DropitServerApplication.class, args);
    }

}
