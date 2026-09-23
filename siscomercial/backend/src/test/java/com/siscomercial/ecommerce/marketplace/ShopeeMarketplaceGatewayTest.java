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

class ShopeeMarketplaceGatewayTest {

    @Test
    void deveIdentificarMarketplaceShopee() {
        ShopeeMarketplaceGateway gateway = gateway(mock(HttpClient.class));
        assertEquals(Marketplace.SHOPEE, gateway.marketplace());
    }

    @Test
    void deveRejeitarPublicacaoSemCategoriaShopee() {
        HttpClient httpClient = mock(HttpClient.class);
        ShopeeMarketplaceGateway gateway = gateway(httpClient);
        IntegracaoMarketplace integracao = integracao();
        Produto produto = produto();

        assertThrows(RegraNegocioException.class, () -> gateway.publicar(integracao, produto));
        verifyNoInteractions(httpClient);
    }

    @Test
    void devePublicarProdutoUsandoImagemEIdRetornadoPelaShopee() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        CredencialMarketplaceService credencialService = mock(CredencialMarketplaceService.class);
        when(credencialService.revelar("token-protegido")).thenReturn("access-token");

        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> imagem = mock(HttpResponse.class);
        when(imagem.statusCode()).thenReturn(200);
        when(imagem.body()).thenReturn(new byte[]{1, 2, 3});

        @SuppressWarnings("unchecked")
        HttpResponse<String> upload = mock(HttpResponse.class);
        when(upload.statusCode()).thenReturn(200);
        when(upload.body()).thenReturn("{\"response\":{\"image_info_list\":[{\"image_info\":{\"image_id\":\"img-1\"}}]}}");

        @SuppressWarnings("unchecked")
        HttpResponse<String> publish = mock(HttpResponse.class);
        when(publish.statusCode()).thenReturn(200);
        when(publish.body()).thenReturn("{\"response\":{\"item_id\":987654321}}");

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            String uri = request.uri().toString();

            if (uri.contains("media_space/upload_image")) {
                return upload;
            }

            if (uri.contains("product/add_item")) {
                return publish;
            }

            return imagem;
        });

        ShopeeMarketplaceGateway gateway = new ShopeeMarketplaceGateway(
                credencialService,
                new ObjectMapper(),
                "123456",
                "partner-key",
                80014L,
                httpClient
        );

        Produto produto = produto();
        produto.setCategoriaShopeeId("100123");
        produto.setPesoShopeeKg(new BigDecimal("0.500"));
        produto.setImagemPrincipal("https://example.com/produto.jpg");

        var resultado = gateway.publicar(integracao(), produto);

        assertEquals("987654321", resultado.identificadorExterno());
        assertEquals("NORMAL", resultado.status());
        verify(httpClient, times(3)).send(any(), any());
    }

    @Test
    void assinaturaShopeeDeveSerDeterministica() {
        String assinatura = ShopeeMarketplaceGateway.assinar(
                "/api/v2/product/add_item",
                1700000000L,
                "123456",
                "chave",
                "token",
                "987654"
        );

        assertEquals(64, assinatura.length());
        assertTrue(assinatura.matches("[0-9a-f]{64}"));
    }

    private ShopeeMarketplaceGateway gateway(HttpClient httpClient) {
        return new ShopeeMarketplaceGateway(
                mock(CredencialMarketplaceService.class),
                new ObjectMapper(),
                "123456",
                "partner-key",
                80014L,
                httpClient
        );
    }

    private IntegracaoMarketplace integracao() {
        IntegracaoMarketplace integracao = new IntegracaoMarketplace();
        integracao.setMarketplace(Marketplace.SHOPEE);
        integracao.setIdentificadorExterno("987654");
        integracao.setTokenProtegido("token-protegido");
        return integracao;
    }

    private Produto produto() {
        Produto produto = new Produto();
        produto.setId(10L);
        produto.setNome("Produto Shopee Teste");
        produto.setCodigoInterno("SKU-001");
        produto.setDescricao("Produto para teste da integracao Shopee");
        produto.setPrecoVenda(new BigDecimal("99.90"));
        produto.setQuantidadeEstoque(10);
        produto.setQuantidadeReservada(0);
        produto.setCategoriaShopeeId(null);
        produto.setPesoShopeeKg(null);
        produto.setImagemPrincipal("https://example.com/produto.jpg");
        return produto;
    }
}
