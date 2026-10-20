package com.siscomercial.ecommerce.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siscomercial.ecommerce.config.AmazonProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Teste manual da autenticação LWA real da Amazon Sandbox.
 *
 * Execute somente quando as variáveis de ambiente estiverem configuradas.
 * Nenhuma credencial ou access token é exibido no resultado do teste.
 */
class AmazonAuthenticationServiceSandboxIntegrationTest {

    @Test
    void deveObterAccessTokenRealDaAmazonSandbox() {
        String clientId = System.getenv("AMAZON_LWA_CLIENT_ID");
        String clientSecret = System.getenv("AMAZON_LWA_CLIENT_SECRET");
        String refreshToken = System.getenv("AMAZON_LWA_REFRESH_TOKEN");
        assumeTrue(present(clientId) && present(clientSecret) && present(refreshToken),
                "Teste real da Amazon Sandbox ignorado: credenciais LWA não configuradas.");

        AmazonProperties properties = new AmazonProperties();
        properties.setClientId(clientId);
        properties.setClientSecret(clientSecret);
        properties.setRefreshToken(refreshToken);

        AmazonAuthenticationService service = new AmazonAuthenticationService(
                properties,
                new AmazonApiClient(),
                new ObjectMapper());

        String accessToken = service.obterAccessToken();

        assertTrue(properties.hasLwaCredentials());
        assertFalse(accessToken.isBlank());
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
