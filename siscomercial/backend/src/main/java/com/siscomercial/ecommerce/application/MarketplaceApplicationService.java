package com.siscomercial.ecommerce.application;

import com.siscomercial.ecommerce.marketplace.IntegracaoMarketplaceService;
import com.siscomercial.ecommerce.marketplace.MarketplaceOrderSyncOrchestratorService;
import com.siscomercial.ecommerce.marketplace.MarketplaceOrchestratorService;
import com.siscomercial.ecommerce.model.IntegracaoMarketplace;
import com.siscomercial.ecommerce.model.PublicacaoMarketplace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Camada de aplicação para operações comerciais multicanal.
 *
 * Controllers, agente IA e futuros adaptadores (como WhatsApp) devem chamar esta
 * camada, evitando que cada canal replique regras de orquestração.
 */
@Service
@RequiredArgsConstructor
public class MarketplaceApplicationService {
    private final IntegracaoMarketplaceService integracaoService;
    private final MarketplaceOrchestratorService marketplaceService;
    private final MarketplaceOrderSyncOrchestratorService orderSyncService;

    @Transactional(readOnly = true)
    public List<IntegracaoMarketplace> listarIntegracoes() {
        return integracaoService.listar();
    }

    @Transactional(readOnly = true)
    public IntegracaoMarketplace buscarIntegracao(Long integracaoId) {
        return integracaoService.buscar(integracaoId);
    }

    @Transactional(readOnly = true)
    public List<PublicacaoMarketplace> listarPublicacoes(Long integracaoId) {
        return marketplaceService.listarPublicacoes(integracaoId);
    }

    @Transactional
    public PublicacaoMarketplace publicarProduto(Long integracaoId, Long produtoId) {
        return marketplaceService.publicar(integracaoId, produtoId);
    }

    @Transactional
    public PublicacaoMarketplace encerrarPublicacao(Long publicacaoId) {
        return marketplaceService.encerrar(publicacaoId);
    }

    @Transactional
    public PublicacaoMarketplace sincronizarPublicacao(Long publicacaoId) {
        return marketplaceService.sincronizar(publicacaoId);
    }

    @Transactional
    public List<PublicacaoMarketplace> sincronizarPublicacoes(Long integracaoId) {
        return marketplaceService.sincronizarPublicacoes(integracaoId);
    }

    @Transactional
    public MarketplaceOrderSyncOrchestratorService.ResultadoSincronizacao sincronizarPedidos(Long integracaoId) {
        return orderSyncService.sincronizar(integracaoId);
    }
}
