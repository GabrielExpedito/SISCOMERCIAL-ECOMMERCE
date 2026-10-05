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
        client = mock(AmazonApiClient.class);
        gateway = new AmazonMarketplaceGateway(properties, authentication, client, new ObjectMapper());
    }

    @Test
    void publicaOfertaComPayloadOficialEDevolveSubmissionId() throws Exception {
        when(authentication.obterAccessToken()).thenReturn("access-test");
        when(client.spApiRequest(eq(properties), anyString(), eq("access-test"))).thenReturn(HttpRequest.newBuilder());
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(202);
        when(response.body()).thenReturn("{\"status\":\"ACCEPTED\",\"submissionId\":\"submission-1\"}");
        when(client.sendSpApi(any())).thenReturn(response);

        var result = gateway.publicar(null, produto());

        assertEquals(Marketplace.AMAZON, gateway.marketplace());
        assertEquals("submission-1", result.identificadorExterno());
        assertEquals("accepted", result.status());
        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).sendSpApi(captor.capture());
        HttpRequest request = captor.getValue();
        assertEquals("PUT", request.method());
        assertTrue(request.uri().getPath().contains("/listings/2021-08-01/items/seller-1/SKU-1"));
        assertTrue(request.uri().getQuery().contains("requirements=LISTING_OFFER_ONLY"));
        assertEquals("access-test", request.headers().firstValue("x-amz-access-token").orElseThrow());
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
    void erroDeAutenticacaoPropagaMensagemSegura() {
        when(authentication.obterAccessToken()).thenThrow(new RegraNegocioException("Amazon LWA recusou a autenticação."));
        assertTrue(assertThrows(RegraNegocioException.class, () -> gateway.publicar(null, produto()))
                .getMessage().contains("LWA recusou"));
        verify(client, never()).sendSpApi(any());
    }

    @Test
    void respostaAmazonComErroOuIncompletaNaoEConsideradaSucesso() throws Exception {
        when(authentication.obterAccessToken()).thenReturn("access-test");
        when(client.spApiRequest(eq(properties), anyString(), eq("access-test"))).thenReturn(HttpRequest.newBuilder());
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        when(client.sendSpApi(any())).thenReturn(response);
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
