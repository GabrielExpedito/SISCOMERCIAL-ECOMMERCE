package com.siscomercial.ecommerce.marketplace;

import com.siscomercial.ecommerce.model.*;
import com.siscomercial.ecommerce.repository.ImportacaoPedidoMarketplaceRepository;
import com.siscomercial.ecommerce.repository.PublicacaoMarketplaceRepository;
import com.siscomercial.ecommerce.service.EstoqueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketplacePedidoSyncServiceTest {
    @Mock ImportacaoPedidoMarketplaceRepository importacaoRepository;
    @Mock PublicacaoMarketplaceRepository publicacaoRepository;
    @Mock EstoqueService estoqueService;

    @Test
    void deveBaixarEstoqueSomenteUmaVezParaVendaPaga() {
        MarketplacePedidoSyncService service = new MarketplacePedidoSyncService(
                importacaoRepository, publicacaoRepository, estoqueService
        );

        IntegracaoMarketplace integracao = integracao();
        Produto produto = produto();
        PublicacaoMarketplace publicacao = publicacao(produto);
        ImportacaoPedidoMarketplace importacao = new ImportacaoPedidoMarketplace();
        importacao.setId(80L);
        importacao.setIntegracao(integracao);
        importacao.setIdentificadorExterno("100");
        importacao.setItens(new java.util.ArrayList<>());
        when(importacaoRepository.findByIntegracaoIdAndIdentificadorExterno(1L, "100"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(importacao));
        when(publicacaoRepository.findByIdentificadorExternoAndIntegracaoId("MLB1", 1L))
                .thenReturn(Optional.of(publicacao));
        when(importacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MarketplaceGateway.ResultadoPedido pedido = pedido("100", "paid", 2);
        service.processar(integracao, pedido);
        service.processar(integracao, pedido);

        verify(estoqueService, times(1)).registrarMovimentacao(
                eq(produto), eq(TipoMovimentacaoEstoque.VENDA), eq(2), isNull(), contains("100")
        );
    }

    @Test
    void deveEstornarEstoqueUmaVezQuandoVendaPagaForCancelada() {
        MarketplacePedidoSyncService service = new MarketplacePedidoSyncService(
                importacaoRepository, publicacaoRepository, estoqueService
        );

        IntegracaoMarketplace integracao = integracao();
        Produto produto = produto();
        PublicacaoMarketplace publicacao = publicacao(produto);
        when(importacaoRepository.findByIntegracaoIdAndIdentificadorExterno(1L, "200"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(importacaoExistente(integracao, produto, 3)));
        when(publicacaoRepository.findByIdentificadorExternoAndIntegracaoId("MLB1", 1L))
                .thenReturn(Optional.of(publicacao));
        when(importacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.processar(integracao, pedido("200", "paid", 3));
        service.processar(integracao, pedido("200", "cancelled", 3));

        verify(estoqueService, times(1)).registrarMovimentacao(
                eq(produto), eq(TipoMovimentacaoEstoque.VENDA), eq(3), isNull(), contains("200")
        );
        verify(estoqueService, times(1)).registrarMovimentacao(
                eq(produto), eq(TipoMovimentacaoEstoque.CANCELAMENTO), eq(3), isNull(), contains("200")
        );
    }

    @Test
    void deveRejeitarVendaQueReferenciaAnuncioNaoVinculado() {
        MarketplacePedidoSyncService service = new MarketplacePedidoSyncService(
                importacaoRepository, publicacaoRepository, estoqueService
        );
        IntegracaoMarketplace integracao = integracao();
        when(importacaoRepository.findByIntegracaoIdAndIdentificadorExterno(1L, "300"))
                .thenReturn(Optional.empty());
        when(publicacaoRepository.findByIdentificadorExternoAndIntegracaoId("MLB999", 1L))
                .thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> service.processar(integracao, pedido("300", "paid", 1))
        );
        verifyNoInteractions(estoqueService);
    }

    private IntegracaoMarketplace integracao() {
        IntegracaoMarketplace i = new IntegracaoMarketplace();
        i.setId(1L);
        i.setMarketplace(Marketplace.MERCADO_LIVRE);
        i.setStatus(StatusIntegracaoMarketplace.ATIVA);
        i.setIdentificadorExterno("123456");
        return i;
    }

    private Produto produto() {
        Produto p = new Produto();
        p.setId(10L);
        p.setCodigoInterno("PROD-TESTE");
        p.setQuantidadeEstoque(10);
        p.setQuantidadeReservada(0);
        return p;
    }

    private PublicacaoMarketplace publicacao(Produto produto) {
        PublicacaoMarketplace p = new PublicacaoMarketplace();
        p.setId(50L);
        p.setProduto(produto);
        p.setIdentificadorExterno("MLB1");
        p.setStatus(StatusPublicacaoMarketplace.PUBLICADA);
        return p;
    }

    private ImportacaoPedidoMarketplace importacaoExistente(IntegracaoMarketplace integracao, Produto produto, int quantidade) {
        ImportacaoPedidoMarketplace i = new ImportacaoPedidoMarketplace();
        i.setId(70L);
        i.setIntegracao(integracao);
        i.setIdentificadorExterno("200");
        i.setStatusExterno("paid");
        ImportacaoItemPedidoMarketplace item = new ImportacaoItemPedidoMarketplace();
        item.setId(71L);
        item.setImportacao(i);
        item.setIdentificadorItemExterno("MLB1");
        item.setProduto(produto);
        item.setQuantidade(quantidade);
        item.setValorUnitario(new BigDecimal("100.00"));
        item.setEstoqueBaixado(true);
        i.setItens(new java.util.ArrayList<>(List.of(item)));
        i.setEstoqueBaixado(true);
        return i;
    }

    private MarketplaceGateway.ResultadoPedido pedido(String id, String status, int quantidade) {
        return new MarketplaceGateway.ResultadoPedido(
                id,
                status,
                null,
                null,
                new BigDecimal("100.00"),
                "BRL",
                List.of(new MarketplaceGateway.ResultadoItemPedido("MLB1", quantidade, new BigDecimal("100.00")))
        );
    }
}
