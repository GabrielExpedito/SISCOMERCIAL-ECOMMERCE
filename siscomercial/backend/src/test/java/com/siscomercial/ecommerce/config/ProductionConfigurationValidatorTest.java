package com.siscomercial.ecommerce.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductionConfigurationValidatorTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProductionConfigurationValidator.class)
            .withPropertyValues("spring.profiles.active=prod");

    @Test
    void perfilProdFalhaSemConfiguracaoObrigatoria() {
        runner.run(context -> {
            assertNotNull(context.getStartupFailure());
            org.junit.jupiter.api.Assertions.assertTrue(context.getStartupFailure().getMessage()
                    .contains("Configuração obrigatória de produção ausente ou insegura."));
        });
    }

    @Test
    void perfilProdAceitaConfiguracaoCompletaComCookieSeguro() {
        runner.withPropertyValues(
                "spring.datasource.url=jdbc:postgresql://db/prod",
                "spring.datasource.username=app",
                "spring.datasource.password=secret",
                "spring.security.oauth2.client.registration.google.client-id=client",
                "spring.security.oauth2.client.registration.google.client-secret=secret",
                "siscomercial.frontend-url=https://shop.example",
                "siscomercial.marketplace.crypto-key=key",
                "server.servlet.session.cookie.secure=true")
                .run(context -> assertNull(context.getStartupFailure()));
    }
}
