package com.dropit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class DropitServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DropitServerApplication.class, args);
    }

}
