package com.tribo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tribo Invest Play — Backend
 *
 * @EnableAsync    → emails e webhooks processados em background (@Async)
 * @EnableScheduling → jobs agendados (expiração de assinatura, notificações)
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class TriboApplication {
    public static void main(String[] args) {
        SpringApplication.run(TriboApplication.class, args);
    }
}
