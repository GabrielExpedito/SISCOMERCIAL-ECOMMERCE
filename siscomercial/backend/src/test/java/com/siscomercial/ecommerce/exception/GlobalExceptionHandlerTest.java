package com.siscomercial.ecommerce.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void erroInternoNaoExpoeMensagemTecnicaNemCausa() {
        var response = handler.handleGenerico(new IllegalStateException(
                "jdbc url, caminho interno e token-falso", new RuntimeException("senha-falsa")));

        assertEquals(500, response.getStatusCode().value());
        String body = response.getBody().toString();
        assertEquals("{erro=Erro interno. Tente novamente mais tarde.}", body);
        assertFalse(body.contains("jdbc"));
        assertFalse(body.contains("token-falso"));
        assertFalse(body.contains("senha-falsa"));
    }
}
