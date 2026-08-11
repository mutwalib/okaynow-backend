package com.okaynow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OkayNowApplication {

    public static void main(String[] args) {
        SpringApplication.run(OkayNowApplication.class, args);
    }
}
