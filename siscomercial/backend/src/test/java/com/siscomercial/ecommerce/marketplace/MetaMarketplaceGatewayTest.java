package com.siscomercial.ecommerce.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.IntegracaoMarketplace;
import com.siscomercial.ecommerce.model.Marketplace;
import com.siscomercial.ecommerce.model.Produto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MetaMarketplaceGatewayTest {

    @Test
    void deveIdentificarMarketplaceMeta() {
        assertEquals(Marketplace.META, gateway(mock(HttpClient.class)).marketplace());
    }

    @Test
    void deveRejeitarPublicacaoSemImagemPublica() {
        HttpClient httpClient = mock(HttpClient.class);
        MetaMarketplaceGateway gateway = gateway(httpClient);
        Produto produto = produto();
        produto.setImagemPrincipal(null);
        produto.setImagens(java.util.List.of());

        assertThrows(RegraNegocioException.class, () -> gateway.publicar(integracao(), produto));
        verifyNoInteractions(httpClient);
    }

    @Test
    void devePublicarComUpsertEPrecoEmCentavos() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        CredencialMarketplaceService credencialService = mock(CredencialMarketplaceService.class);
        when(credencialService.revelar("token-protegido")).thenReturn("meta-token");

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"id\":\"123456789\",\"visibility\":\"published\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        MetaMarketplaceGateway gateway = new MetaMarketplaceGateway(
                credencialService, new ObjectMapper(), "v24.0", "https://loja.example.com", httpClient);

        var resultado = gateway.publicar(integracao(), produto());

        assertEquals("123456789", resultado.identificadorExterno());
        assertEquals("published", resultado.status());
        var request = captureRequest(httpClient);
        assertEquals("https://graph.facebook.com/v24.0/catalog-123/products", request.uri().toString());
        assertEquals("Bearer meta-token", request.headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    void deveSincronizarProdutoOcultoComoEncerrado() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        CredencialMarketplaceService credencialService = mock(CredencialMarketplaceService.class);
        when(credencialService.revelar("token-protegido")).thenReturn("meta-token");

        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"id\":\"123\",\"visibility\":\"hidden\",\"inventory\":4,\"url\":\"https://facebook.com/p/123\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        var resultado = gateway(credencialService, httpClient).sincronizar(integracao(), "123");

        assertEquals("hidden", resultado.status());
        assertEquals(4, resultado.quantidadeDisponivel());
        assertEquals("https://facebook.com/p/123", resultado.permalink());
    }

    private MetaMarketplaceGateway gateway(HttpClient httpClient) {
        return gateway(mock(CredencialMarketplaceService.class), httpClient);
    }

    private MetaMarketplaceGateway gateway(CredencialMarketplaceService credencialService, HttpClient httpClient) {
        when(credencialService.revelar("token-protegido")).thenReturn("meta-token");
        return new MetaMarketplaceGateway(credencialService, new ObjectMapper(), "v24.0", "https://loja.example.com", httpClient);
    }

    private HttpRequest captureRequest(HttpClient httpClient) throws Exception {
        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        return captor.getValue();
    }

    private IntegracaoMarketplace integracao() {
        IntegracaoMarketplace integracao = new IntegracaoMarketplace();
        integracao.setMarketplace(Marketplace.META);
        integracao.setIdentificadorExterno("catalog-123");
        integracao.setTokenProtegido("token-protegido");
        return integracao;
    }

    private Produto produto() {
        Produto produto = new Produto();
        produto.setId(1L);
        produto.setNome("Produto Meta Teste");
        produto.setCodigoInterno("PROD-001");
        produto.setDescricao("Produto para teste da integracao Meta");
        produto.setPrecoVenda(new BigDecimal("99.90"));
        produto.setQuantidadeEstoque(10);
        produto.setQuantidadeReservada(0);
        produto.setImagemPrincipal("https://cdn.example.com/produto.jpg");
        return produto;
    }
}
