package com.siscomercial.ecommerce.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siscomercial.ecommerce.config.AmazonProperties;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AmazonAuthenticationServiceTest {
    @Test
    void propriedadesAmazonSaoExternalizadasSemExporSecretsEmToString() {
        AmazonProperties properties = configurada();

        assertTrue(properties.hasLwaCredentials());
        assertEquals("seller-example", properties.getSellerId());
        assertEquals("marketplace-example", properties.getMarketplaceId());
        assertFalse(properties.toString().contains("client-secret-test"));
        assertFalse(properties.toString().contains("refresh-token-test"));
    }

    @Test
    void clienteConstroiRequisicaoLwaEHeadersSpApiSemEnviarChamadasReais() throws Exception {
        var httpClient = mock(java.net.http.HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"access_token\":\"access-test\"}");
        when(httpClient.send(any(), any(java.net.http.HttpResponse.BodyHandler.class))).thenReturn(response);
        AmazonApiClient client = new AmazonApiClient(httpClient);

        client.postForm("https://auth.example/token", java.util.Map.of("grant_type", "refresh_token"));

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(java.net.http.HttpResponse.BodyHandler.class));
        assertEquals("application/x-www-form-urlencoded;charset=UTF-8",
                requestCaptor.getValue().headers().firstValue("Content-Type").orElseThrow());

        var spApiRequest = client.spApiRequest(configurada(), "/listings/2021-08-01/items", "access-test").GET().build();
        assertEquals("access-test", spApiRequest.headers().firstValue("x-amz-access-token").orElseThrow());
        assertTrue(spApiRequest.headers().firstValue("x-amz-date").orElseThrow().matches("\\d{8}T\\d{6}Z"));
        assertTrue(spApiRequest.headers().firstValue("user-agent").orElseThrow().contains("Java/17"));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void autenticaComMockEDevolveSomenteAccessToken() throws Exception {
        AmazonProperties properties = configurada();
        AmazonApiClient client = mock(AmazonApiClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"access_token\":\"access-test\",\"expires_in\":3600}");
        when(client.postForm(eq(properties.getLwaTokenUrl()), anyMap())).thenReturn(response);

        String token = new AmazonAuthenticationService(properties, client, new ObjectMapper()).obterAccessToken();

        assertEquals("access-test", token);
        verify(client).postForm(eq(properties.getLwaTokenUrl()), argThat(fields ->
                "refresh_token".equals(fields.get("grant_type"))
                        && properties.getRefreshToken().equals(fields.get("refresh_token"))
                        && properties.getClientId().equals(fields.get("client_id"))
                        && properties.getClientSecret().equals(fields.get("client_secret"))));
    }

    @Test
    void erroLwaERespostaInvalidaNaoExibemCredenciais() throws Exception {
        AmazonProperties properties = configurada();
        AmazonApiClient client = mock(AmazonApiClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> unauthorized = mock(HttpResponse.class);
        when(unauthorized.statusCode()).thenReturn(401);
        when(unauthorized.body()).thenReturn("client-secret-test refresh-token-test");
        when(client.postForm(anyString(), anyMap())).thenReturn(unauthorized);

        RegraNegocioException authError = assertThrows(RegraNegocioException.class,
                () -> new AmazonAuthenticationService(properties, client, new ObjectMapper()).obterAccessToken());
        assertFalse(authError.getMessage().contains("client-secret-test"));
        assertFalse(authError.getMessage().contains("refresh-token-test"));

        when(unauthorized.statusCode()).thenReturn(200);
        when(unauthorized.body()).thenReturn("{\"error\":\"invalid_grant\"}");
        RegraNegocioException invalidResponse = assertThrows(RegraNegocioException.class,
                () -> new AmazonAuthenticationService(properties, client, new ObjectMapper()).obterAccessToken());
        assertTrue(invalidResponse.getMessage().contains("inválida"));
    }

    @Test
    void ausenciaDeCredenciaisFalhaAntesDeChamarCliente() {
        AmazonApiClient client = mock(AmazonApiClient.class);
        RegraNegocioException error = assertThrows(RegraNegocioException.class,
                () -> new AmazonAuthenticationService(new AmazonProperties(), client, new ObjectMapper()).obterAccessToken());

        assertTrue(error.getMessage().contains("não configuradas"));
        verifyNoInteractions(client);
    }

    private AmazonProperties configurada() {
        AmazonProperties properties = new AmazonProperties();
        properties.setClientId("client-id-test");
        properties.setClientSecret("client-secret-test");
        properties.setRefreshToken("refresh-token-test");
        properties.setSellerId("seller-example");
        properties.setMarketplaceId("marketplace-example");
        return properties;
    }
}
