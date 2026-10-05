package com.siscomercial.ecommerce.marketplace;

import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.*;
import com.siscomercial.ecommerce.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketplaceOrchestratorServiceTest {
    @Mock IntegracaoMarketplaceRepository integracaoRepository;
    @Mock PublicacaoMarketplaceRepository publicacaoRepository;
    @Mock HistoricoIntegracaoMarketplaceRepository historicoRepository;
    @Mock ProdutoRepository produtoRepository;

    @Test
    void devePublicarPorGatewayEPersistirResultado() {
        MarketplaceGateway gateway = new MarketplaceGateway() {
            public Marketplace marketplace() { return Marketplace.MERCADO_LIVRE; }
            public ResultadoPublicacao publicar(IntegracaoMarketplace i, Produto p) { return new ResultadoPublicacao("MLB1", "https://ml.test/MLB1", "active"); }
            public ResultadoOperacao encerrar(IntegracaoMarketplace i, String id) { return new ResultadoOperacao("closed"); }
            public ResultadoSincronizacao sincronizar(IntegracaoMarketplace i, String id) { return new ResultadoSincronizacao("active", 5, "https://ml.test/MLB1"); }
        };
        MarketplaceOrchestratorService service = new MarketplaceOrchestratorService(integracaoRepository, publicacaoRepository,
                historicoRepository, produtoRepository, List.of(gateway));
        IntegracaoMarketplace integracao = new IntegracaoMarketplace(); integracao.setId(2L); integracao.setMarketplace(Marketplace.MERCADO_LIVRE); integracao.setStatus(StatusIntegracaoMarketplace.ATIVA);
        Produto produto = new Produto(); produto.setId(3L); produto.setQuantidadeEstoque(5); produto.setQuantidadeReservada(0); produto.setPrecoVenda(new BigDecimal("10.00"));
        when(integracaoRepository.findById(2L)).thenReturn(Optional.of(integracao));
        when(produtoRepository.findById(3L)).thenReturn(Optional.of(produto));
        when(publicacaoRepository.existsByProdutoIdAndIntegracaoIdAndStatusIn(eq(3L), eq(2L), anyList())).thenReturn(false);
        when(publicacaoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PublicacaoMarketplace resultado = service.publicar(2L, 3L);

        assertEquals("MLB1", resultado.getIdentificadorExterno());
        assertEquals(StatusPublicacaoMarketplace.PUBLICADA, resultado.getStatus());
        verify(historicoRepository).save(any(HistoricoIntegracaoMarketplace.class));
    }

    @Test
    void devePermitirNovaPublicacaoQuandoAnteriorEstaEncerrada() {
        MarketplaceGateway gateway = new MarketplaceGateway() {
            public Marketplace marketplace() { return Marketplace.MERCADO_LIVRE; }
            public ResultadoPublicacao publicar(IntegracaoMarketplace i, Produto p) { return new ResultadoPublicacao("MLB2", "https://ml.test/MLB2", "active"); }
            public ResultadoOperacao encerrar(IntegracaoMarketplace i, String id) { return new ResultadoOperacao("closed"); }
            public ResultadoSincronizacao sincronizar(IntegracaoMarketplace i, String id) { return new ResultadoSincronizacao("active", 5, "https://ml.test/MLB2"); }
        };
        MarketplaceOrchestratorService service = new MarketplaceOrchestratorService(integracaoRepository, publicacaoRepository,
                historicoRepository, produtoRepository, List.of(gateway));

        IntegracaoMarketplace integracao = new IntegracaoMarketplace();
        integracao.setId(2L);
        integracao.setMarketplace(Marketplace.MERCADO_LIVRE);
        integracao.setStatus(StatusIntegracaoMarketplace.ATIVA);

        Produto produto = new Produto();
        produto.setId(3L);
        produto.setQuantidadeEstoque(5);
        produto.setQuantidadeReservada(0);
        produto.setPrecoVenda(new BigDecimal("10.00"));

        when(integracaoRepository.findById(2L)).thenReturn(Optional.of(integracao));
        when(produtoRepository.findById(3L)).thenReturn(Optional.of(produto));
        when(publicacaoRepository.existsByProdutoIdAndIntegracaoIdAndStatusIn(eq(3L), eq(2L), anyList())).thenReturn(false);
        when(publicacaoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PublicacaoMarketplace resultado = service.publicar(2L, 3L);

        assertEquals("MLB2", resultado.getIdentificadorExterno());
        assertEquals(StatusPublicacaoMarketplace.PUBLICADA, resultado.getStatus());
        verify(publicacaoRepository).save(any(PublicacaoMarketplace.class));
    }

    @Test
    void deveRejeitarNovaPublicacaoQuandoExistePublicacaoBloqueadora() {
        MarketplaceGateway gateway = new MarketplaceGateway() {
            public Marketplace marketplace() { return Marketplace.MERCADO_LIVRE; }
            public ResultadoPublicacao publicar(IntegracaoMarketplace i, Produto p) {
                return new ResultadoPublicacao("ML-BLOQUEADO", "https://ml.test/ML-BLOQUEADO", "active");
            }
            public ResultadoOperacao encerrar(IntegracaoMarketplace i, String id) {
                return new ResultadoOperacao("closed");
            }
            public ResultadoSincronizacao sincronizar(IntegracaoMarketplace i, String id) {
                return new ResultadoSincronizacao("active", 5, "https://ml.test/ML-BLOQUEADO");
            }
        };
        MarketplaceOrchestratorService service = new MarketplaceOrchestratorService(integracaoRepository, publicacaoRepository,
                historicoRepository, produtoRepository, List.of(gateway));

        IntegracaoMarketplace integracao = new IntegracaoMarketplace();
        integracao.setId(2L);
        integracao.setMarketplace(Marketplace.MERCADO_LIVRE);
        integracao.setStatus(StatusIntegracaoMarketplace.ATIVA);

        Produto produto = new Produto();
        produto.setId(3L);

        when(integracaoRepository.findById(2L)).thenReturn(Optional.of(integracao));
        when(produtoRepository.findById(3L)).thenReturn(Optional.of(produto));
        when(publicacaoRepository.existsByProdutoIdAndIntegracaoIdAndStatusIn(eq(3L), eq(2L), anyList())).thenReturn(true);

        assertThrows(RegraNegocioException.class, () -> service.publicar(2L, 3L));
        verify(publicacaoRepository, never()).save(any());
    }

    @Test
    void deveRejeitarPublicacaoEmIntegracaoInativa() {
        MarketplaceOrchestratorService service = new MarketplaceOrchestratorService(integracaoRepository, publicacaoRepository,
                historicoRepository, produtoRepository, List.of());
        IntegracaoMarketplace integracao = new IntegracaoMarketplace(); integracao.setStatus(StatusIntegracaoMarketplace.INATIVA);
        when(integracaoRepository.findById(2L)).thenReturn(Optional.of(integracao));

        assertThrows(RegraNegocioException.class, () -> service.publicar(2L, 3L));
        verifyNoInteractions(produtoRepository, publicacaoRepository, historicoRepository);
    }

    @Test
    void publicacaoMulticanalSemIntegracoesAtivasRetornaListaVazia() {
        when(integracaoRepository.findByStatus(StatusIntegracaoMarketplace.ATIVA)).thenReturn(List.of());
        MarketplaceOrchestratorService service = service(List.of());

        assertTrue(service.publicarMulticanal(3L).isEmpty());
        verifyNoInteractions(produtoRepository, publicacaoRepository, historicoRepository);
    }

    @Test
    void publicacaoMulticanalComUmaIntegracaoChamaGatewayEPersisteResultado() {
        IntegracaoMarketplace ml = integracao(1L, Marketplace.MERCADO_LIVRE);
        Produto produto = produto(3L);
        MarketplaceGateway gateway = gateway(Marketplace.MERCADO_LIVRE, "MLB-1");
        prepararMulticanal(produto, ml);
        MarketplaceOrchestratorService service = service(List.of(gateway));

        List<PublicacaoMarketplace> resultado = service.publicarMulticanal(3L);

        assertEquals(1, resultado.size());
        assertEquals(Marketplace.MERCADO_LIVRE, resultado.get(0).getIntegracao().getMarketplace());
        assertEquals(StatusPublicacaoMarketplace.PUBLICADA, resultado.get(0).getStatus());
        assertEquals("MLB-1", resultado.get(0).getIdentificadorExterno());
        verify(gateway).publicar(ml, produto);
        verify(publicacaoRepository).save(argThat(p -> p.getStatus() == StatusPublicacaoMarketplace.PUBLICADA));
    }

    @Test
    void publicacaoMulticanalChamaOsDoisGatewaysEContinuaQuandoPrimeiroFalha() {
        IntegracaoMarketplace ml = integracao(1L, Marketplace.MERCADO_LIVRE);
        IntegracaoMarketplace shopee = integracao(2L, Marketplace.SHOPEE);
        Produto produto = produto(3L);
        MarketplaceGateway gatewayMl = gatewayComFalha(Marketplace.MERCADO_LIVRE, "Falha ML");
        MarketplaceGateway gatewayShopee = gateway(Marketplace.SHOPEE, "SHOP-2");
        prepararMulticanal(produto, ml, shopee);
        MarketplaceOrchestratorService service = service(List.of(gatewayMl, gatewayShopee));

        List<PublicacaoMarketplace> resultado = service.publicarMulticanal(3L);

        assertEquals(2, resultado.size());
        assertEquals(StatusPublicacaoMarketplace.ERRO, resultado.get(0).getStatus());
        assertEquals("Falha ML", resultado.get(0).getUltimoErro());
        assertEquals(StatusPublicacaoMarketplace.PUBLICADA, resultado.get(1).getStatus());
        assertEquals("SHOP-2", resultado.get(1).getIdentificadorExterno());
        verify(gatewayMl).publicar(ml, produto);
        verify(gatewayShopee).publicar(shopee, produto);
        verify(publicacaoRepository, times(2)).save(any(PublicacaoMarketplace.class));
    }

    @Test
    void falhaDoSegundoCanalPreservaPublicacaoDoPrimeiro() {
        IntegracaoMarketplace ml = integracao(1L, Marketplace.MERCADO_LIVRE);
        IntegracaoMarketplace shopee = integracao(2L, Marketplace.SHOPEE);
        Produto produto = produto(3L);
        MarketplaceGateway gatewayMl = gateway(Marketplace.MERCADO_LIVRE, "MLB-1");
        MarketplaceGateway gatewayShopee = gatewayComFalha(Marketplace.SHOPEE, "Falha Shopee");
        prepararMulticanal(produto, ml, shopee);
        MarketplaceOrchestratorService service = service(List.of(gatewayMl, gatewayShopee));

        List<PublicacaoMarketplace> resultado = service.publicarMulticanal(3L);

        assertEquals(StatusPublicacaoMarketplace.PUBLICADA, resultado.get(0).getStatus());
        assertEquals("MLB-1", resultado.get(0).getIdentificadorExterno());
        assertEquals(StatusPublicacaoMarketplace.ERRO, resultado.get(1).getStatus());
        assertEquals("Falha Shopee", resultado.get(1).getUltimoErro());
        verify(gatewayMl).publicar(ml, produto);
        verify(gatewayShopee).publicar(shopee, produto);
    }

    @Test
    void falhaAmazonNaoImpedeMercadoLivreEShopee() {
        IntegracaoMarketplace amazon = integracao(1L, Marketplace.AMAZON);
        IntegracaoMarketplace ml = integracao(2L, Marketplace.MERCADO_LIVRE);
        IntegracaoMarketplace shopee = integracao(3L, Marketplace.SHOPEE);
        Produto produto = produto(3L);
        MarketplaceGateway amazonGateway = gatewayComFalha(Marketplace.AMAZON, "Falha Amazon");
        MarketplaceGateway gatewayMl = gateway(Marketplace.MERCADO_LIVRE, "MLB-OK");
        MarketplaceGateway gatewayShopee = gateway(Marketplace.SHOPEE, "SHOP-OK");
        prepararMulticanal(produto, amazon, ml, shopee);
        MarketplaceOrchestratorService service = service(List.of(amazonGateway, gatewayMl, gatewayShopee));

        List<PublicacaoMarketplace> resultado = service.publicarMulticanal(3L);

        assertEquals(StatusPublicacaoMarketplace.ERRO, resultado.get(0).getStatus());
        assertEquals("Falha Amazon", resultado.get(0).getUltimoErro());
        assertEquals(StatusPublicacaoMarketplace.PUBLICADA, resultado.get(1).getStatus());
        assertEquals("MLB-OK", resultado.get(1).getIdentificadorExterno());
        assertEquals(StatusPublicacaoMarketplace.PUBLICADA, resultado.get(2).getStatus());
        verify(amazonGateway).publicar(amazon, produto);
        verify(gatewayMl).publicar(ml, produto);
        verify(gatewayShopee).publicar(shopee, produto);
    }

    @Test
    void falhaMercadoLivreNaoImpedePublicacaoAmazon() {
        IntegracaoMarketplace ml = integracao(1L, Marketplace.MERCADO_LIVRE);
        IntegracaoMarketplace amazon = integracao(2L, Marketplace.AMAZON);
        Produto produto = produto(3L);
        MarketplaceGateway gatewayMl = gatewayComFalha(Marketplace.MERCADO_LIVRE, "Falha ML");
        MarketplaceGateway gatewayAmazon = gateway(Marketplace.AMAZON, "submission-1");
        prepararMulticanal(produto, ml, amazon);
        var resultado = service(List.of(gatewayMl, gatewayAmazon)).publicarMulticanal(3L);
        assertEquals(StatusPublicacaoMarketplace.ERRO, resultado.get(0).getStatus());
        assertEquals(StatusPublicacaoMarketplace.PUBLICADA, resultado.get(1).getStatus());
        assertEquals("submission-1", resultado.get(1).getIdentificadorExterno());
    }

    @Test
    void falhaShopeeNaoImpedePublicacaoAmazon() {
        IntegracaoMarketplace shopee = integracao(1L, Marketplace.SHOPEE);
        IntegracaoMarketplace amazon = integracao(2L, Marketplace.AMAZON);
        Produto produto = produto(3L);
        MarketplaceGateway gatewayShopee = gatewayComFalha(Marketplace.SHOPEE, "Falha Shopee");
        MarketplaceGateway gatewayAmazon = gateway(Marketplace.AMAZON, "submission-1");
        prepararMulticanal(produto, shopee, amazon);
        var resultado = service(List.of(gatewayShopee, gatewayAmazon)).publicarMulticanal(3L);
        assertEquals(StatusPublicacaoMarketplace.ERRO, resultado.get(0).getStatus());
        assertEquals(StatusPublicacaoMarketplace.PUBLICADA, resultado.get(1).getStatus());
    }

    @Test
    void amazonEMercadoLivreShopeePublicamIndependentes() {
        IntegracaoMarketplace ml = integracao(1L, Marketplace.MERCADO_LIVRE);
        IntegracaoMarketplace shopee = integracao(2L, Marketplace.SHOPEE);
        IntegracaoMarketplace amazon = integracao(3L, Marketplace.AMAZON);
        Produto produto = produto(3L);
        var gateways = List.of(gateway(Marketplace.MERCADO_LIVRE, "MLB-1"),
                gateway(Marketplace.SHOPEE, "SHOP-1"), gateway(Marketplace.AMAZON, "submission-1"));
        prepararMulticanal(produto, ml, shopee, amazon);
        var resultado = service(gateways).publicarMulticanal(3L);
        assertEquals(3, resultado.size());
        assertEquals(StatusPublicacaoMarketplace.PUBLICADA, resultado.get(0).getStatus());
        assertEquals(StatusPublicacaoMarketplace.PUBLICADA, resultado.get(1).getStatus());
        assertEquals(StatusPublicacaoMarketplace.PUBLICADA, resultado.get(2).getStatus());
    }

    @Test
    void persisteSubmissionIdAmazonComoEmAnaliseAteProcessamentoDaAmazon() {
        IntegracaoMarketplace amazon = integracao(1L, Marketplace.AMAZON);
        Produto produto = produto(3L);
        MarketplaceGateway gatewayAmazon = mock(MarketplaceGateway.class);
        when(gatewayAmazon.marketplace()).thenReturn(Marketplace.AMAZON);
        when(gatewayAmazon.publicar(any(), any())).thenReturn(
                new MarketplaceGateway.ResultadoPublicacao("submission-1", null, "accepted"));
        prepararMulticanal(produto, amazon);
        MarketplaceOrchestratorService service = service(List.of(gatewayAmazon));

        List<PublicacaoMarketplace> resultado = service.publicarMulticanal(3L);

        assertEquals("submission-1", resultado.get(0).getIdentificadorExterno());
        assertEquals(StatusPublicacaoMarketplace.EM_ANALISE, resultado.get(0).getStatus());
        verify(publicacaoRepository).save(argThat(p -> "submission-1".equals(p.getIdentificadorExterno())
                && p.getStatus() == StatusPublicacaoMarketplace.EM_ANALISE));
    }

    private MarketplaceOrchestratorService service(List<MarketplaceGateway> gateways) {
        return new MarketplaceOrchestratorService(integracaoRepository, publicacaoRepository,
                historicoRepository, produtoRepository, gateways);
    }

    private void prepararMulticanal(Produto produto, IntegracaoMarketplace... integracoes) {
        when(integracaoRepository.findByStatus(StatusIntegracaoMarketplace.ATIVA)).thenReturn(List.of(integracoes));
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));
        for (IntegracaoMarketplace integracao : integracoes) {
            when(publicacaoRepository.existsByProdutoIdAndIntegracaoIdAndStatusIn(
                    produto.getId(), integracao.getId(), List.of(
                            StatusPublicacaoMarketplace.PENDENTE, StatusPublicacaoMarketplace.PUBLICADA,
                            StatusPublicacaoMarketplace.PAUSADA, StatusPublicacaoMarketplace.EM_ANALISE,
                            StatusPublicacaoMarketplace.AGUARDANDO_ATIVACAO, StatusPublicacaoMarketplace.INATIVA,
                            StatusPublicacaoMarketplace.ERRO))).thenReturn(false);
        }
        when(publicacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private IntegracaoMarketplace integracao(Long id, Marketplace marketplace) {
        IntegracaoMarketplace integracao = new IntegracaoMarketplace();
        integracao.setId(id);
        integracao.setMarketplace(marketplace);
        integracao.setStatus(StatusIntegracaoMarketplace.ATIVA);
        return integracao;
    }

    private Produto produto(Long id) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setQuantidadeEstoque(4);
        produto.setQuantidadeReservada(0);
        produto.setPrecoVenda(new BigDecimal("10.00"));
        return produto;
    }

    private MarketplaceGateway gateway(Marketplace marketplace, String externalId) {
        MarketplaceGateway gateway = mock(MarketplaceGateway.class);
        when(gateway.marketplace()).thenReturn(marketplace);
        when(gateway.publicar(any(), any())).thenReturn(
                new MarketplaceGateway.ResultadoPublicacao(externalId, "https://example.test/" + externalId, "active"));
        return gateway;
    }

    private MarketplaceGateway gatewayComFalha(Marketplace marketplace, String message) {
        MarketplaceGateway gateway = mock(MarketplaceGateway.class);
        when(gateway.marketplace()).thenReturn(marketplace);
        when(gateway.publicar(any(), any())).thenThrow(new RegraNegocioException(message));
        return gateway;
    }

}
