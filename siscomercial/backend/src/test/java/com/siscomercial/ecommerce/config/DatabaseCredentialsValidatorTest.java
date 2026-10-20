package com.siscomercial.ecommerce.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseCredentialsValidatorTest {
    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void configurarContexto() {
        contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(DatabaseCredentialsValidator.class);
    }

    @Test
    void aceitaConfiguracaoCompleta() {
        contextRunner.withPropertyValues(
                "spring.datasource.url=jdbc:postgresql://localhost:5432/siscomercial",
                "spring.datasource.username=usuario-local",
                "spring.datasource.password=senha-local")
                .run(context -> assertNull(context.getStartupFailure()));
    }

    @Test
    void rejeitaConfiguracaoAusenteOuVaziaSemExporValores() {
        contextRunner.run(context -> assertNotNull(context.getStartupFailure()));
        contextRunner.withPropertyValues(
                "spring.datasource.url=jdbc:postgresql://localhost/db",
                "spring.datasource.username= ",
                "spring.datasource.password=senha-local")
                .run(context -> assertNotNull(context.getStartupFailure()));
        contextRunner.withPropertyValues(
                "spring.datasource.url=jdbc:postgresql://localhost/db",
                "spring.datasource.username=usuario-local",
                "spring.datasource.password=")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void rejeitaPlaceholdersNaoResolvidosDoSpring() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DatabaseCredentialsValidator.validate("${DB_URL}", "${DB_USERNAME}", "${DB_PASSWORD}"));

        assertNotNull(error.getMessage());
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("DB_URL, DB_USERNAME and DB_PASSWORD"));
    }
}
