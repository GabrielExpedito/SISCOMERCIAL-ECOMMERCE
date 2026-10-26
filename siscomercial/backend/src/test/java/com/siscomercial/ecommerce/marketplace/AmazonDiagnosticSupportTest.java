package com.siscomercial.ecommerce.marketplace;

import com.siscomercial.ecommerce.config.AmazonProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AmazonDiagnosticSupportTest {
    @Test
    void tokenNuloOuVazioERepresentadoSemImprimirCredencial() {
        AmazonProperties properties = new AmazonProperties();
        String token = "sensitive-access-token";

        String nullo = AmazonDiagnosticSupport.tokenDiagnostic(null, null, properties, null, null);
        String vazio = AmazonDiagnosticSupport.tokenDiagnostic("", "", properties, null, null);

        assertTrue(nullo.contains("lwaTokenNull=true"));
        assertTrue(nullo.contains("lwaTokenEmpty=true"));
        assertTrue(vazio.contains("spApiTokenEmpty=true"));
        assertTrue(nullo.contains("lwaTokenId=unavailable"));
        assertFalse(nullo.contains("TokenLength"));
        assertFalse(nullo.contains("tokensEqual"));
        assertFalse(nullo.contains(token));
        assertFalse(vazio.contains(token));
    }

    @Test
    void tokensIguaisComHmacProduzemMesmoIdentificadorEIncluemHorarios() {
        AmazonProperties properties = new AmazonProperties();
        properties.setTokenDiagnosticHmacKey("diagnostic-key-kept-out-of-logs");
        Instant obtainedAt = Instant.parse("2026-10-15T12:00:00Z");
        Instant requestAt = Instant.parse("2026-10-15T12:00:02Z");

        String diagnostic = AmazonDiagnosticSupport.tokenDiagnostic(
                "private-token-value", "private-token-value", properties, obtainedAt, requestAt);

        String lwaId = field(diagnostic, "lwaTokenId");
        String spApiId = field(diagnostic, "spApiTokenId");
        assertEquals(lwaId, spApiId);
        assertNotEquals("unavailable", lwaId);
        assertTrue(diagnostic.contains(obtainedAt.toString()));
        assertTrue(diagnostic.contains(requestAt.toString()));
        assertFalse(diagnostic.contains("private-token-value"));
        assertFalse(diagnostic.contains("diagnostic-key-kept-out-of-logs"));
    }

    @Test
    void divergenciaDosTokensFicaIdentificadaSemExibirValores() {
        AmazonProperties properties = new AmazonProperties();
        properties.setTokenDiagnosticHmacKey("diagnostic-key");

        String diagnostic = AmazonDiagnosticSupport.tokenDiagnostic(
                "lwa-token-value", "header-token-value", properties, Instant.now(), Instant.now());

        assertNotEquals(field(diagnostic, "lwaTokenId"), field(diagnostic, "spApiTokenId"));
        assertFalse(diagnostic.contains("lwa-token-value"));
        assertFalse(diagnostic.contains("header-token-value"));
    }

    private String field(String diagnostic, String name) {
        return java.util.Arrays.stream(diagnostic.split(" "))
                .filter(part -> part.startsWith(name + "="))
                .map(part -> part.substring(name.length() + 1))
                .findFirst().orElseThrow();
    }
}
