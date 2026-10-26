package com.siscomercial.ecommerce.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("prod")
public class ProductionConfigurationValidator implements InitializingBean {
    private final Environment environment;

    public ProductionConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> required = List.of(
                "spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
                "spring.security.oauth2.client.registration.google.client-id",
                "spring.security.oauth2.client.registration.google.client-secret",
                "siscomercial.frontend-url", "siscomercial.marketplace.crypto-key");
        boolean missing = required.stream().anyMatch(key -> {
            String value = environment.getProperty(key);
            return value == null || value.isBlank() || value.startsWith("${");
        });
        if (missing || !environment.getProperty("server.servlet.session.cookie.secure", Boolean.class, false)) {
            throw new IllegalStateException("Configuração obrigatória de produção ausente ou insegura.");
        }
    }
}
