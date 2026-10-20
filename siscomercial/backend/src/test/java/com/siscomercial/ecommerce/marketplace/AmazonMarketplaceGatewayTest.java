package com.siscomercial.ecommerce.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siscomercial.ecommerce.config.AmazonProperties;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.Marketplace;
import com.siscomercial.ecommerce.model.Produto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AmazonMarketplaceGatewayTest {
    private AmazonProperties properties;
    private AmazonAuthenticationService authentication;
    private AmazonApiClient client;
    private AmazonMarketplaceGateway gateway;

    @BeforeEach
    void setup() {
        properties = new AmazonProperties();
        properties.setClientId("test-client");
        properties.setClientSecret("test-secret");
        properties.setRefreshToken("test-refresh");
        properties.setSellerId("seller-1");
        properties.setMarketplaceId("market-1");
        authentication = mock(AmazonAuthenticationService.class);
        client = spy(new AmazonApiClient());
        gateway = new AmazonMarketplaceGateway(properties, authentication, client, new ObjectMapper());
    }

    @Test
    void publicaOfertaComPayloadOficialEDevolveSubmissionId() throws Exception {
        when(authentication.obterAccessToken()).thenReturn("access-test");
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(202);
        when(response.body()).thenReturn("{\"status\":\"ACCEPTED\",\"submissionId\":\"submission-1\"}");
        doReturn(response).when(client).sendSpApi(any());

        var result = gateway.publicar(null, produto());

        assertEquals(Marketplace.AMAZON, gateway.marketplace());
        assertEquals("submission-1", result.identificadorExterno());
        assertEquals("accepted", result.status());
        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).sendSpApi(captor.capture());
        HttpRequest request = captor.getValue();
        assertEquals("PUT", request.method());
        assertTrue(request.uri().getPath().contains("/listings/2021-08-01/items/seller-1/SKU-1"));
        assertEquals("marketplaceIds=market-1&issueLocale=pt_BR", request.uri().getRawQuery());
        assertEquals("access-test", request.headers().firstValue("x-amz-access-token").orElseThrow());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("application/json", request.headers().firstValue("Accept").orElseThrow());
        var payload = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                gateway, "montarPayload", "B000123456", new BigDecimal("19.90"), 3);
        assertEquals("LISTING_OFFER_ONLY", ((java.util.Map<?, ?>) payload).get("requirements"));
        var attributes = (java.util.Map<?, ?>) ((java.util.Map<?, ?>) payload).get("attributes");
        assertTrue(attributes.containsKey("condition_type"));
        assertTrue(attributes.containsKey("merchant_suggested_asin"));
        assertTrue(attributes.containsKey("fulfillment_availability"));
        assertTrue(attributes.containsKey("purchasable_offer"));
        var offer = (java.util.Map<?, ?>) ((java.util.List<?>) attributes.get("purchasable_offer")).get(0);
        assertEquals("BRL", offer.get("currency"));
    }

    @Test
    void codificaSkuComoSegmentoEnaoExpoeSegredosNasMensagensDeErro() throws Exception {
        properties.setClientSecret("client-secret-value");
        properties.setRefreshToken("refresh-token-value");
        when(authentication.obterAccessToken()).thenReturn("access-token-value");
        Produto produto = produto();
        produto.setCodigoInterno("SKU X/1");
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(client).sendSpApi(any());
        when(response.statusCode()).thenReturn(403);
        when(response.body()).thenReturn("{\"errors\":[{\"code\":\"Unauthorized\",\"message\":\"denied access-token-value client-secret-value refresh-token-value\"}]}");

        RegraNegocioException ex = assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto));

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).sendSpApi(captor.capture());
        assertTrue(captor.getValue().uri().getRawPath().endsWith("/SKU%20X%2F1"));
        assertTrue(ex.getMessage().contains("SP_API_AUTHORIZATION"));
        assertTrue(ex.getMessage().contains("Unauthorized"));
        assertFalse(ex.getMessage().contains("access-token-value"));
        assertFalse(ex.getMessage().contains("client-secret-value"));
        assertFalse(ex.getMessage().contains("refresh-token-value"));
    }

    @Test
    void classificaFalhasHttpDeValidacaoEIndisponibilidade() throws Exception {
        when(authentication.obterAccessToken()).thenReturn("access-test");
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(client).sendSpApi(any());

        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn("{\"errors\":[{\"code\":\"InvalidInput\",\"message\":\"bad request\"}]}");
        assertTrue(assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto()))
                .getMessage().contains("SP_API_VALIDATION"));

        when(response.statusCode()).thenReturn(401);
        when(response.body()).thenReturn("{\"errors\":[{\"code\":\"Unauthorized\",\"message\":\"invalid token\"}]}");
        assertTrue(assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto()))
                .getMessage().contains("SP_API_AUTHORIZATION"));

        when(response.statusCode()).thenReturn(503);
        when(response.body()).thenReturn("{\"errors\":[{\"code\":\"ServiceUnavailable\",\"message\":\"try later\"}]}");
        assertTrue(assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto()))
                .getMessage().contains("SP_API_AVAILABILITY"));
    }

    @Test
    void falhaDeRedeSpApiPreservaCausaOriginalSemExporDetalhesDaRequisicao() throws Exception {
        when(authentication.obterAccessToken()).thenReturn("access-test");
        doThrow(new java.io.IOException("socket closed")).when(client).sendSpApi(any());

        RegraNegocioException error = assertThrows(RegraNegocioException.class,
                () -> gateway.publicar(null, produto()));

        assertTrue(error.getMessage().contains("SP_API_COMMUNICATION"));
        assertInstanceOf(java.io.IOException.class, error.getCause());
        assertFalse(error.getMessage().contains("socket closed"));
    }

    @Test
    void rejeitaProdutoSemAsinAntesDeAutenticar() {
        Produto produto = produto();
        produto.setAsinAmazon(null);
        assertTrue(assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto))
                .getMessage().contains("ASIN válido"));
        verifyNoInteractions(authentication, client);
    }

    @Test
    void credenciaisAusentesFalhamSemChamadaExterna() {
        properties.setRefreshToken("");
        assertTrue(assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto()))
                .getMessage().contains("Credenciais LWA"));
        verifyNoInteractions(authentication, client);
    }

    @Test
    void accessTokenNuloOuVazioNaoEEnviadoAoHeaderSpApi() throws Exception {
        when(authentication.obterAccessToken()).thenReturn(null, "   ");

        RegraNegocioException nulo = assertThrows(RegraNegocioException.class,
                () -> gateway.publicar(null, produto()));
        RegraNegocioException vazio = assertThrows(RegraNegocioException.class,
                () -> gateway.publicar(null, produto()));

        assertTrue(nulo.getMessage().contains("access token vazio"));
        assertTrue(vazio.getMessage().contains("access token vazio"));
        verify(client, never()).sendSpApi(any());
    }

    @Test
    void erroDeAutenticacaoPropagaMensagemSegura() throws Exception {
        when(authentication.obterAccessToken()).thenThrow(new RegraNegocioException("Amazon LWA recusou a autenticação."));
        assertTrue(assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto()))
                .getMessage().contains("LWA recusou"));
        verify(client, never()).sendSpApi(any());
    }

    @Test
    void respostaAmazonComErroOuIncompletaNaoEConsideradaSucesso() throws Exception {
        when(authentication.obterAccessToken()).thenReturn("access-test");
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(client).sendSpApi(any());
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn("{}");
        assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto()));
        when(response.statusCode()).thenReturn(202);
        when(response.body()).thenReturn("{\"status\":\"INVALID\",\"issues\":[{\"message\":\"invalid listing\"}]}");
        assertTrue(assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto()))
                .getMessage().contains("invalid listing"));
        when(response.body()).thenReturn("{}");
        assertTrue(assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto()))
                .getMessage().contains("incompleta"));
    }

    @Test
    void outrasOperacoesAmazonPermanecemForaDoEscopo() {
        assertThrows(RegraNegocioException.class, () -> gateway.buscarPedidos(null, null));
        assertThrows(RegraNegocioException.class, () -> gateway.sincronizar(null, null));
        assertThrows(RegraNegocioException.class, () -> gateway.encerrar(null, null));
    }

    @Test
    void cenario1_http403ComArrayErrors() throws Exception {
        when(authentication.obterAccessToken()).thenReturn("access-test");
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(client).sendSpApi(any());
        when(response.statusCode()).thenReturn(403);
        when(response.body()).thenReturn("""
            {
              "errors": [
                {
                  "code": "Unauthorized",
                  "message": "Access to requested resource is denied."
                }
              ]
            }
            """);

        RegraNegocioException ex = assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto()));
        assertTrue(ex.getMessage().contains("HTTP 403"));
        assertTrue(ex.getMessage().contains("Unauthorized"));
        assertTrue(ex.getMessage().contains("Access to requested resource is denied."));
    }

    @Test
    void cenario2_http403ComArrayIssues() throws Exception {
        when(authentication.obterAccessToken()).thenReturn("access-test");
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(client).sendSpApi(any());
        when(response.statusCode()).thenReturn(403);
        when(response.body()).thenReturn("""
            {
              "issues": [
                {
                  "code": "90000",
                  "message": "The merchant_suggested_asin provided does not exist."
                }
              ]
            }
            """);

        RegraNegocioException ex = assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto()));
        assertTrue(ex.getMessage().contains("HTTP 403"));
        assertTrue(ex.getMessage().contains("SP_API_VALIDATION"));
        assertTrue(ex.getMessage().contains("90000"));
        assertTrue(ex.getMessage().contains("The merchant_suggested_asin provided does not exist."));
    }

    @Test
    void cenario3_respostaSemCorpoPreservaStatusHttpSemLancarExcecaoSecundaria() throws Exception {
        when(authentication.obterAccessToken()).thenReturn("access-test");
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(client).sendSpApi(any());
        when(response.statusCode()).thenReturn(403);
        when(response.body()).thenReturn("");

        RegraNegocioException ex = assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto()));
        assertTrue(ex.getMessage().contains("HTTP 403"));
        assertFalse(ex.getMessage().contains("NullPointerException"));

        when(response.body()).thenReturn(null);
        RegraNegocioException exNull = assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto()));
        assertTrue(exNull.getMessage().contains("HTTP 403"));
    }

    @Test
    void cenario4_jsonInvalidoPreservaStatusHttpOriginal() throws Exception {
        when(authentication.obterAccessToken()).thenReturn("access-test");
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(client).sendSpApi(any());
        when(response.statusCode()).thenReturn(403);
        when(response.body()).thenReturn("<html><body>403 Forbidden - Gateway Error</body></html>");

        RegraNegocioException ex = assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto()));
        assertTrue(ex.getMessage().contains("HTTP 403"));
        assertFalse(ex.getMessage().contains("JsonParseException"));
        assertFalse(ex.getMessage().contains("Falha ao publicar na Amazon SP-API."));
    }

    @Test
    void cenario5_identificadorDaRequisicaoCapturadoDosHeaders() throws Exception {
        when(authentication.obterAccessToken()).thenReturn("access-test");
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(client).sendSpApi(any());
        when(response.statusCode()).thenReturn(403);
        when(response.body()).thenReturn("""
            {
              "errors": [
                {
                  "code": "Unauthorized",
                  "message": "Access denied"
                }
              ]
            }
            """);
        java.net.http.HttpHeaders headers = java.net.http.HttpHeaders.of(
                java.util.Map.of("x-amzn-RequestId", java.util.List.of("c0d20912-87ad-4598-a37f-cf12e022da33")),
                (k, v) -> true);
        when(response.headers()).thenReturn(headers);

        RegraNegocioException ex = assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto()));
        assertTrue(ex.getMessage().contains("HTTP 403"));
        assertTrue(ex.getMessage().contains("c0d20912-87ad-4598-a37f-cf12e022da33"));
        assertTrue(ex.getMessage().contains("Request ID: c0d20912-87ad-4598-a37f-cf12e022da33"));
        assertTrue(ex.getMessage().contains("SP_API_AUTHORIZATION"));
    }

    @Test
    void resposta403TokenRevogadoPreservaStatusERequestId() throws Exception {
        when(authentication.obterAccessToken()).thenReturn("access-test");
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(client).sendSpApi(any());
        when(response.statusCode()).thenReturn(403);
        when(response.body()).thenReturn("{\"errors\":[{\"code\":\"Unauthorized\",\"message\":\"Access to requested resource is denied.\",\"details\":\"The access token you provided is revoked, malformed or invalid.\"}]}");
        when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(
                java.util.Map.of("x-amzn-RequestId", java.util.List.of("9f16552e-feb6-4664-a5b7-8085bcf4975c")),
                (k, v) -> true));

        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> gateway.publicar(null, produto()));

        assertTrue(ex.getMessage().contains("HTTP 403"));
        assertTrue(ex.getMessage().contains("SP_API_AUTHORIZATION"));
        assertTrue(ex.getMessage().contains("revoked, malformed or invalid"));
        assertTrue(ex.getMessage().contains("Request ID: 9f16552e-feb6-4664-a5b7-8085bcf4975c"));
    }

    @Test
    void cenario6_respostaBemSucedidaContinuaOperandoNormalmente() throws Exception {
        when(authentication.obterAccessToken()).thenReturn("access-test");
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(client).sendSpApi(any());
        when(response.statusCode()).thenReturn(202);
        when(response.body()).thenReturn("{\"status\":\"ACCEPTED\",\"submissionId\":\"sub-valid-999\"}");

        var resultado = gateway.publicar(null, produto());
        assertNotNull(resultado);
        assertEquals("sub-valid-999", resultado.identificadorExterno());
        assertEquals("accepted", resultado.status());
    }

    @Test
    void cenario7_multiplosErrosApresentadosDeFormaLegivel() throws Exception {
        when(authentication.obterAccessToken()).thenReturn("access-test");
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(client).sendSpApi(any());
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn("""
            {
              "errors": [
                {
                  "code": "InvalidInput",
                  "message": "SKU informado é inválido."
                },
                {
                  "code": "QuotaExceeded",
                  "message": "Limite de chamadas atingido.",
                  "details": "Aguarde 60 segundos antes de reenviar."
                }
              ]
            }
            """);

        RegraNegocioException ex = assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto()));
        assertTrue(ex.getMessage().contains("HTTP 400"));
        assertTrue(ex.getMessage().contains("InvalidInput"));
        assertTrue(ex.getMessage().contains("SKU informado é inválido."));
        assertTrue(ex.getMessage().contains("QuotaExceeded"));
        assertTrue(ex.getMessage().contains("Limite de chamadas atingido."));
        assertTrue(ex.getMessage().contains("Aguarde 60 segundos"));
        assertTrue(ex.getMessage().contains(";"));
    }

    @Test
    void cenario8_evitaDuplicacaoDeMensagensERespeitaDetails() throws Exception {
        when(authentication.obterAccessToken()).thenReturn("access-test");
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(response).when(client).sendSpApi(any());
        when(response.statusCode()).thenReturn(403);
        when(response.body()).thenReturn("""
            {
              "errors": [
                {
                  "code": "AccessDenied",
                  "message": "Acesso não permitido.",
                  "details": "Acesso não permitido."
                },
                {
                  "code": "AccessDenied",
                  "message": "Acesso não permitido."
                }
              ]
            }
            """);

        RegraNegocioException ex = assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto()));
        String msg = ex.getMessage();
        int ocorrencias = (msg.length() - msg.replace("Acesso não permitido", "").length()) / "Acesso não permitido".length();
        assertEquals(1, ocorrencias, "Não deve duplicar a mensagem idêntica");
    }

    private Produto produto() {
        Produto produto = new Produto();
        produto.setCodigoInterno("SKU-1");
        produto.setAsinAmazon("B000123456");
        produto.setPrecoVenda(new BigDecimal("19.90"));
        produto.setQuantidadeEstoque(4);
        produto.setQuantidadeReservada(1);
        return produto;
    }
}
