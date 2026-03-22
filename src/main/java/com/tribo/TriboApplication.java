package com.tribo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada da aplicação Tribo Invest Play.
 *
 * @EnableCaching  — ativa o cache Redis com @Cacheable
 * @EnableAsync    — permite métodos assíncronos com @Async (webhooks)
 * @EnableScheduling — permite jobs agendados com @Scheduled (flush de progresso)
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class TriboApplication {
    public static void main(String[] args) {
        SpringApplication.run(TriboApplication.class, args);
    }
}
