package com.tribo.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Inicializa o SDK do Stripe com a chave secreta ao subir a aplicação.
 * Sem isso, chamadas como Session.create() falham silenciosamente.
 */
@Configuration
@Slf4j
public class StripeConfig {

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @PostConstruct
    public void init() {
        if (secretKey != null && !secretKey.isBlank() && !secretKey.startsWith("sk_test_placeholder")) {
            Stripe.apiKey = secretKey;
            log.info("Stripe SDK inicializado (key: {}...)", secretKey.substring(0, Math.min(12, secretKey.length())));
        } else {
            log.warn("STRIPE_SECRET_KEY não configurada — pagamentos desabilitados");
        }
    }
}
