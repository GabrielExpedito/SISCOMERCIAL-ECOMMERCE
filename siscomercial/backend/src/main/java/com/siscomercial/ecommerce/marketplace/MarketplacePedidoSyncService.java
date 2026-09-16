package com.siscomercial.ecommerce.marketplace;

import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.*;
import com.siscomercial.ecommerce.repository.ImportacaoPedidoMarketplaceRepository;
import com.siscomercial.ecommerce.repository.PublicacaoMarketplaceRepository;
import com.siscomercial.ecommerce.service.EstoqueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Processa uma venda externa em transação única, garantindo idempotência da baixa de estoque. */
@Service
@RequiredArgsConstructor
public class MarketplacePedidoSyncService {
    private final ImportacaoPedidoMarketplaceRepository importacaoRepository;
    private final PublicacaoMarketplaceRepository publicacaoRepository;
    private final EstoqueService estoqueService;

    @Transactional
    public void processar(IntegracaoMarketplace integracao, MarketplaceGateway.ResultadoPedido pedido) {
        if (pedido.identificadorExterno() == null || pedido.identificadorExterno().isBlank()) {
            throw new RegraNegocioException("O Mercado Livre retornou uma venda sem identificador externo.");
        }
        if (pedido.itens() == null || pedido.itens().isEmpty()) {
            throw new RegraNegocioException("A venda " + pedido.identificadorExterno() + " não possui itens processáveis.");
        }

        ImportacaoPedidoMarketplace importacao = importacaoRepository
                .findByIntegracaoIdAndIdentificadorExterno(integracao.getId(), pedido.identificadorExterno())
                .orElseGet(() -> novaImportacao(integracao, pedido));

        validarAlteracaoDeQuantidade(importacao, pedido);
        importacao.setStatusExterno(pedido.status());
        importacao.setDataCriacaoExterna(pedido.dataCriacao());
        importacao.setDataAtualizacaoExterna(pedido.dataAtualizacao());
        importacao.setTotal(pedido.total());
        importacao.setMoeda(pedido.moeda());
        importacao.setUltimoErro(null);

        boolean vendaConfirmada = "paid".equalsIgnoreCase(pedido.status());
        boolean vendaCancelada = "cancelled".equalsIgnoreCase(pedido.status());

        if (!vendaConfirmada && !vendaCancelada) {
            importacaoRepository.save(importacao);
            return;
        }

        for (MarketplaceGateway.ResultadoItemPedido item : pedido.itens()) {
            PublicacaoMarketplace publicacao = publicacaoRepository
                    .findByIdentificadorExternoAndIntegracaoId(item.identificadorItemExterno(), integracao.getId())
                    .orElseThrow(() -> new RegraNegocioException(
                            "A venda " + pedido.identificadorExterno()
                                    + " referencia o anúncio " + item.identificadorItemExterno()
                                    + ", mas o anúncio não está vinculado a um produto do Siscomercial."
                    ));

            ImportacaoItemPedidoMarketplace importacaoItem = importacao.getItens().stream()
                    .filter(i -> item.identificadorItemExterno().equals(i.getIdentificadorItemExterno()))
                    .findFirst()
                    .orElse(null);

            if (importacaoItem == null) {
                importacaoItem = criarItem(importacao, publicacao.getProduto(), item);
                importacao.getItens().add(importacaoItem);
            }

            if (vendaConfirmada && !importacaoItem.isEstoqueBaixado()) {
                estoqueService.registrarMovimentacao(
                        publicacao.getProduto(),
                        TipoMovimentacaoEstoque.VENDA,
                        item.quantidade(),
                        null,
                        "Venda Mercado Livre " + pedido.identificadorExterno()
                );
                importacaoItem.setEstoqueBaixado(true);
            } else if (vendaCancelada && importacaoItem.isEstoqueBaixado()) {
                estoqueService.registrarMovimentacao(
                        publicacao.getProduto(),
                        TipoMovimentacaoEstoque.CANCELAMENTO,
                        item.quantidade(),
                        null,
                        "Cancelamento Mercado Livre " + pedido.identificadorExterno()
                );
                importacaoItem.setEstoqueBaixado(false);
            }

            publicacao.setQuantidadePublicada(publicacao.getProduto().getQuantidadeDisponivel());
            publicacao.setUltimaSincronizacao(LocalDateTime.now());
        }

        importacao.setEstoqueBaixado(importacao.getItens().stream().allMatch(ImportacaoItemPedidoMarketplace::isEstoqueBaixado));
        if (vendaCancelada) {
            importacao.setEstoqueBaixado(false);
        }
        importacaoRepository.save(importacao);
    }

    private ImportacaoPedidoMarketplace novaImportacao(IntegracaoMarketplace integracao, MarketplaceGateway.ResultadoPedido pedido) {
        ImportacaoPedidoMarketplace importacao = new ImportacaoPedidoMarketplace();
        importacao.setIntegracao(integracao);
        importacao.setIdentificadorExterno(pedido.identificadorExterno());
        importacao.setImportadoEm(LocalDateTime.now());
        return importacao;
    }

    private ImportacaoItemPedidoMarketplace criarItem(
            ImportacaoPedidoMarketplace importacao,
            Produto produto,
            MarketplaceGateway.ResultadoItemPedido item) {
        ImportacaoItemPedidoMarketplace novo = new ImportacaoItemPedidoMarketplace();
        novo.setImportacao(importacao);
        novo.setIdentificadorItemExterno(item.identificadorItemExterno());
        novo.setProduto(produto);
        novo.setQuantidade(item.quantidade());
        novo.setValorUnitario(item.valorUnitario());
        return novo;
    }

    private void validarAlteracaoDeQuantidade(
            ImportacaoPedidoMarketplace importacao,
            MarketplaceGateway.ResultadoPedido pedido) {
        for (MarketplaceGateway.ResultadoItemPedido item : pedido.itens()) {
            importacao.getItens().stream()
                    .filter(i -> item.identificadorItemExterno().equals(i.getIdentificadorItemExterno()))
                    .findFirst()
                    .ifPresent(existing -> {
                        if (existing.getQuantidade() != item.quantidade()) {
                            throw new RegraNegocioException(
                                    "A quantidade da venda " + pedido.identificadorExterno()
                                            + " foi alterada pelo marketplace após o processamento. Requer revisão manual antes de nova baixa."
                            );
                        }
                    });
        }
    }
}
