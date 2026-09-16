package com.siscomercial.ecommerce.marketplace;

import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.exception.RecursoNaoEncontradoException;
import com.siscomercial.ecommerce.model.IntegracaoMarketplace;
import com.siscomercial.ecommerce.model.Marketplace;
import com.siscomercial.ecommerce.model.StatusHistoricoIntegracaoMarketplace;
import com.siscomercial.ecommerce.model.StatusIntegracaoMarketplace;
import com.siscomercial.ecommerce.repository.HistoricoIntegracaoMarketplaceRepository;
import com.siscomercial.ecommerce.repository.IntegracaoMarketplaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Orquestra a importação de vendas sem expor APIs específicas ao restante do domínio. */
@Service
@RequiredArgsConstructor
public class MarketplaceOrderSyncOrchestratorService {
    private final IntegracaoMarketplaceRepository integracaoRepository;
    private final HistoricoIntegracaoMarketplaceRepository historicoRepository;
    private final MarketplacePedidoSyncService pedidoSyncService;
    private final List<MarketplaceGateway> gateways;

    public ResultadoSincronizacao sincronizar(Long integracaoId) {
        IntegracaoMarketplace integracao = integracaoRepository.findById(integracaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Integracao de marketplace nao encontrada: " + integracaoId));
        if (integracao.getStatus() != StatusIntegracaoMarketplace.ATIVA) {
            throw new RegraNegocioException("A integração deve estar ativa para sincronizar vendas.");
        }

        MarketplaceGateway gateway = gateways.stream()
                .collect(Collectors.toMap(MarketplaceGateway::marketplace, Function.identity()))
                .get(integracao.getMarketplace());
        if (gateway == null) {
            throw new RegraNegocioException("Marketplace ainda nao possui gateway configurado.");
        }

        LocalDateTime inicio = LocalDateTime.now();
        List<MarketplaceGateway.ResultadoPedido> pedidos = gateway.buscarPedidos(
                integracao,
                integracao.getUltimaSincronizacaoPedidos()
        );

        int processados = 0;
        int falhas = 0;
        for (MarketplaceGateway.ResultadoPedido pedido : pedidos) {
            try {
                pedidoSyncService.processar(integracao, pedido);
                processados++;
            } catch (RuntimeException ex) {
                falhas++;
                registrarHistorico(
                        integracao,
                        "SINCRONIZAR_PEDIDO",
                        pedido.identificadorExterno(),
                        StatusHistoricoIntegracaoMarketplace.FALHA,
                        ex.getMessage()
                );
            }
        }

        // Se houver falhas, não avançamos o cursor. A próxima sincronização repetirá
        // a janela anterior e a idempotência impedirá novas baixas para as vendas já processadas.
        if (falhas == 0) {
            integracao.setUltimaSincronizacaoPedidos(inicio);
            integracao.setUltimaSincronizacao(LocalDateTime.now());
            integracaoRepository.save(integracao);
        }

        registrarHistorico(
                integracao,
                "SINCRONIZAR_PEDIDOS",
                null,
                falhas == 0 ? StatusHistoricoIntegracaoMarketplace.SUCESSO : StatusHistoricoIntegracaoMarketplace.FALHA,
                "Pedidos consultados: " + pedidos.size() + ". Processados: " + processados + ". Falhas: " + falhas + "."
        );

        return new ResultadoSincronizacao(pedidos.size(), processados, falhas,
                falhas == 0 ? integracao.getUltimaSincronizacaoPedidos() : null);
    }

    private void registrarHistorico(IntegracaoMarketplace integracao, String operacao, String referencia,
                                    StatusHistoricoIntegracaoMarketplace status, String mensagem) {
        var historico = new com.siscomercial.ecommerce.model.HistoricoIntegracaoMarketplace();
        historico.setIntegracao(integracao);
        historico.setOperacao(operacao);
        historico.setReferenciaExterna(referencia);
        historico.setStatus(status);
        historico.setMensagemTecnica(mensagem == null ? null : mensagem.substring(0, Math.min(mensagem.length(), 1000)));
        historicoRepository.save(historico);
    }

    public record ResultadoSincronizacao(int consultados, int processados, int falhas, LocalDateTime sincronizadoEm) {}
}
