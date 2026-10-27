package com.siscomercial.ecommerce.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.IntegracaoMarketplace;
import com.siscomercial.ecommerce.model.Marketplace;
import com.siscomercial.ecommerce.model.Produto;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

        java.util.List<HttpRequest> requests = new java.util.ArrayList<>();

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            requests.add(request);
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

        HttpRequest publishRequest = requests.stream()
                .filter(request -> request.uri().getPath().endsWith("/api/v2/product/add_item"))
                .findFirst().orElseThrow();
        java.util.Map<String, String> query = query(publishRequest.uri().getRawQuery());
        assertEquals("123456", query.get("partner_id"));
        assertEquals("987654", query.get("shop_id"));
        assertEquals("access-token", query.get("access_token"));
        assertTrue(query.containsKey("timestamp"));
        assertEquals(ShopeeMarketplaceGateway.assinar("/api/v2/product/add_item",
                Long.parseLong(query.get("timestamp")), "123456", "partner-key", "access-token", "987654"),
                query.get("sign"));
    }

    @Test
    void falhaDeTransporteNaoExpoeTokensEmMensagemOuLog() throws Exception {
        String accessToken = "token-sensivel-de-teste";
        String partnerKey = "chave-sensivel-de-teste";
        String erroInterno = "https://host.test/path?access_token=" + accessToken + "&sign=assinatura-sensivel";
        HttpClient httpClient = mock(HttpClient.class);
        CredencialMarketplaceService credencialService = mock(CredencialMarketplaceService.class);
        when(credencialService.revelar("token-protegido")).thenReturn(accessToken);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException(erroInterno));
        ShopeeMarketplaceGateway gateway = new ShopeeMarketplaceGateway(
                credencialService, new ObjectMapper(), "123456", partnerKey, 80014L, httpClient);

        Logger logger = (Logger) LoggerFactory.getLogger(ShopeeMarketplaceGateway.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            RegraNegocioException error = assertThrows(RegraNegocioException.class,
                    () -> gateway.publicar(integracao(), produtoValido()));
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertFalse(error.getMessage().contains(accessToken));
            assertFalse(error.getMessage().contains(partnerKey));
            assertFalse(error.getMessage().contains("assinatura-sensivel"));
            assertFalse(logs.contains(accessToken));
            assertFalse(logs.contains(partnerKey));
            assertFalse(logs.contains("assinatura-sensivel"));
            assertFalse(logs.contains("host.test"));
        } finally {
            logger.detachAppender(appender);
        }
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

    private Produto produtoValido() {
        Produto produto = produto();
        produto.setCategoriaShopeeId("100123");
        produto.setPesoShopeeKg(new BigDecimal("0.500"));
        return produto;
    }

    private java.util.Map<String, String> query(String rawQuery) {
        return java.util.Arrays.stream(rawQuery.split("&"))
                .map(part -> part.split("=", 2))
                .collect(java.util.stream.Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8)));
    }
}
