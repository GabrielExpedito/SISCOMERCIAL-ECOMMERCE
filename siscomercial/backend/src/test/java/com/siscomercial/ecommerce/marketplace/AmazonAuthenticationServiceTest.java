package com.siscomercial.ecommerce.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siscomercial.ecommerce.config.AmazonProperties;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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
        when(unauthorized.body()).thenReturn("{\"error\":\"invalid_grant\",\"error_description\":\"client-secret-test refresh-token-test email-falso@example.test\"}");
        when(unauthorized.headers()).thenReturn(java.net.http.HttpHeaders.of(
                java.util.Map.of("x-amzn-RequestId", java.util.List.of("lwa-request-123")), (k, v) -> true));
        when(client.postForm(anyString(), anyMap())).thenReturn(unauthorized);

        RegraNegocioException authError = assertThrows(RegraNegocioException.class,
                () -> new AmazonAuthenticationService(properties, client, new ObjectMapper()).obterAccessToken());
        assertFalse(authError.getMessage().contains("client-secret-test"));
        assertFalse(authError.getMessage().contains("refresh-token-test"));
        assertFalse(authError.getMessage().contains("email-falso@example.test"));
        assertTrue(authError.getMessage().contains("LWA_AUTH"));
        assertTrue(authError.getMessage().contains("invalid_grant"));
        assertTrue(authError.getMessage().contains("Request ID: lwa-request-123"));

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

    @Test
    void logDeFalhaLwaOmiteCredenciaisETrechosDaResposta() throws Exception {
        AmazonProperties properties = configurada();
        AmazonApiClient client = mock(AmazonApiClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(response.body()).thenReturn("{\"error\":\"invalid_grant\",\"error_description\":"
                + "\"client-secret-test refresh-token-test email-falso@example.test\"}");
        when(client.postForm(anyString(), anyMap())).thenReturn(response);

        Logger logger = (Logger) LoggerFactory.getLogger(AmazonAuthenticationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThrows(RegraNegocioException.class,
                    () -> new AmazonAuthenticationService(properties, client, new ObjectMapper()).obterAccessToken());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(logged.contains("invalid_grant"));
        assertFalse(logged.contains("client-secret-test"));
        assertFalse(logged.contains("refresh-token-test"));
        assertFalse(logged.contains("email-falso@example.test"));
    }

    @Test
    void falhaDeRedeLwaPreservaCausaOriginalSemExibirCredenciais() throws Exception {
        AmazonApiClient client = mock(AmazonApiClient.class);
        when(client.postForm(anyString(), anyMap())).thenThrow(new java.io.IOException("network unavailable"));

        RegraNegocioException error = assertThrows(RegraNegocioException.class,
                () -> new AmazonAuthenticationService(configurada(), client, new ObjectMapper()).obterAccessToken());

        assertTrue(error.getMessage().contains("LWA_COMMUNICATION"));
        assertInstanceOf(java.io.IOException.class, error.getCause());
        assertFalse(error.getMessage().contains("network unavailable"));
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
