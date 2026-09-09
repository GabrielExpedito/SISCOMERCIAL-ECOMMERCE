package com.siscomercial.ecommerce.marketplace;

import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.exception.RecursoNaoEncontradoException;
import com.siscomercial.ecommerce.model.*;
import com.siscomercial.ecommerce.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Coordena gateways e persiste o resultado de cada canal sem duplicar regras de produto. */
@Service
public class MarketplaceOrchestratorService {
    private final IntegracaoMarketplaceRepository integracaoRepository;
    private final PublicacaoMarketplaceRepository publicacaoRepository;
    private final HistoricoIntegracaoMarketplaceRepository historicoRepository;
    private final ProdutoRepository produtoRepository;
    private final Map<Marketplace, MarketplaceGateway> gateways;

    public MarketplaceOrchestratorService(IntegracaoMarketplaceRepository integracaoRepository,
                                          PublicacaoMarketplaceRepository publicacaoRepository,
                                          HistoricoIntegracaoMarketplaceRepository historicoRepository,
                                          ProdutoRepository produtoRepository,
                                          java.util.List<MarketplaceGateway> gateways) {
        this.integracaoRepository = integracaoRepository;
        this.publicacaoRepository = publicacaoRepository;
        this.historicoRepository = historicoRepository;
        this.produtoRepository = produtoRepository;
        this.gateways = gateways.stream().collect(Collectors.toMap(MarketplaceGateway::marketplace, Function.identity()));
    }

    @Transactional
    public PublicacaoMarketplace publicar(Long integracaoId, Long produtoId) {
        IntegracaoMarketplace integracao = integracaoRepository.findById(integracaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Integracao de marketplace nao encontrada: " + integracaoId));
        if (integracao.getStatus() != StatusIntegracaoMarketplace.ATIVA) {
            throw new RegraNegocioException("A integracao deve estar ativa para publicar produtos.");
        }
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto nao encontrado: id " + produtoId));
        MarketplaceGateway gateway = gateways.get(integracao.getMarketplace());
        if (gateway == null) throw new RegraNegocioException("Marketplace ainda nao possui gateway configurado.");
        if (publicacaoRepository.findByProdutoIdAndIntegracaoId(produtoId, integracaoId).isPresent()) {
            throw new RegraNegocioException("O produto ja possui publicacao nesta integracao.");
        }

        try {
            MarketplaceGateway.ResultadoPublicacao resultado = gateway.publicar(integracao, produto);
            PublicacaoMarketplace publicacao = new PublicacaoMarketplace();
            publicacao.setProduto(produto);
            publicacao.setIntegracao(integracao);
            publicacao.setIdentificadorExterno(resultado.identificadorExterno());
            publicacao.setUrlPublicacao(resultado.urlPublicacao());
            publicacao.setStatus(StatusPublicacaoMarketplace.PUBLICADA);
            publicacao.setQuantidadePublicada(produto.getQuantidadeDisponivel());
            publicacao.setUltimaSincronizacao(LocalDateTime.now());
            publicacao = publicacaoRepository.save(publicacao);
            registrarHistorico(integracao, "PUBLICAR_PRODUTO", resultado.identificadorExterno(), StatusHistoricoIntegracaoMarketplace.SUCESSO, null);
            return publicacao;
        } catch (RuntimeException ex) {
            registrarHistorico(integracao, "PUBLICAR_PRODUTO", null, StatusHistoricoIntegracaoMarketplace.FALHA, ex.getMessage());
            throw ex;
        }
    }

    private void registrarHistorico(IntegracaoMarketplace integracao, String operacao, String referencia,
                                    StatusHistoricoIntegracaoMarketplace status, String mensagem) {
        HistoricoIntegracaoMarketplace historico = new HistoricoIntegracaoMarketplace();
        historico.setIntegracao(integracao);
        historico.setOperacao(operacao);
        historico.setReferenciaExterna(referencia);
        historico.setStatus(status);
        historico.setMensagemTecnica(mensagem == null ? null : mensagem.substring(0, Math.min(mensagem.length(), 1000)));
        historicoRepository.save(historico);
    }
}
