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

    @Transactional(readOnly = true)
    public java.util.List<PublicacaoMarketplace> listarPublicacoes(Long integracaoId) {
        if (integracaoId != null) {
            if (!integracaoRepository.existsById(integracaoId)) {
                throw new RecursoNaoEncontradoException("Integracao de marketplace nao encontrada: " + integracaoId);
            }
            return publicacaoRepository.findByIntegracaoIdOrderByUltimaSincronizacaoDesc(integracaoId);
        }
        return publicacaoRepository.findAllByOrderByUltimaSincronizacaoDesc();
    }

    @Transactional
    public java.util.List<PublicacaoMarketplace> sincronizarPublicacoes(Long integracaoId) {
        java.util.List<PublicacaoMarketplace> publicacoes = listarPublicacoes(integracaoId);
        for (PublicacaoMarketplace publicacao : publicacoes) {
            try {
                sincronizar(publicacao.getId());
            } catch (RuntimeException ignored) {
                // A falha de um anúncio não impede a sincronização dos demais.
            }
        }
        return listarPublicacoes(integracaoId);
    }

    @Transactional
    public PublicacaoMarketplace sincronizar(Long publicacaoId) {
        PublicacaoMarketplace publicacao = publicacaoRepository.findById(publicacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Publicacao de marketplace nao encontrada: " + publicacaoId));

        IntegracaoMarketplace integracao = publicacao.getIntegracao();
        MarketplaceGateway gateway = gateways.get(integracao.getMarketplace());
        if (gateway == null) {
            throw new RegraNegocioException("Marketplace ainda nao possui gateway configurado.");
        }

        try {
            MarketplaceGateway.ResultadoSincronizacao resultado =
                    gateway.sincronizar(integracao, publicacao.getIdentificadorExterno());
            publicacao.setStatus(mapearStatus(resultado.status()));
            publicacao.setQuantidadePublicada(resultado.quantidadeDisponivel());
            if (resultado.permalink() != null && !resultado.permalink().isBlank()) {
                publicacao.setUrlPublicacao(resultado.permalink());
            }
            publicacao.setUltimaSincronizacao(LocalDateTime.now());
            publicacao.setUltimoErro(null);
            return publicacaoRepository.save(publicacao);
        } catch (RuntimeException ex) {
            publicacao.setUltimaSincronizacao(LocalDateTime.now());
            publicacao.setUltimoErro(ex.getMessage());
            publicacaoRepository.save(publicacao);
            registrarHistorico(integracao, "SINCRONIZAR_PUBLICACAO", publicacao.getIdentificadorExterno(),
                    StatusHistoricoIntegracaoMarketplace.FALHA, ex.getMessage());
            throw ex;
        }
    }

    private StatusPublicacaoMarketplace mapearStatus(String status) {
        if (status == null) return StatusPublicacaoMarketplace.ERRO;
        return switch (status.toLowerCase()) {
            case "active" -> StatusPublicacaoMarketplace.PUBLICADA;
            case "paused" -> StatusPublicacaoMarketplace.PAUSADA;
            case "closed" -> StatusPublicacaoMarketplace.ENCERRADA;
            case "under_review" -> StatusPublicacaoMarketplace.EM_ANALISE;
            case "not_yet_active" -> StatusPublicacaoMarketplace.AGUARDANDO_ATIVACAO;
            case "inactive" -> StatusPublicacaoMarketplace.INATIVA;
            default -> StatusPublicacaoMarketplace.ERRO;
        };
    }

    @Transactional
    public PublicacaoMarketplace encerrar(Long publicacaoId) {
        PublicacaoMarketplace publicacao = publicacaoRepository.findById(publicacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Publicacao de marketplace nao encontrada: " + publicacaoId));

        if (publicacao.getStatus() == StatusPublicacaoMarketplace.ENCERRADA) {
            throw new RegraNegocioException("O anuncio ja esta encerrado.");
        }
        if (publicacao.getStatus() != StatusPublicacaoMarketplace.PUBLICADA
                && publicacao.getStatus() != StatusPublicacaoMarketplace.PAUSADA) {
            throw new RegraNegocioException("Somente anuncios publicados ou pausados podem ser encerrados.");
        }

        IntegracaoMarketplace integracao = publicacao.getIntegracao();
        MarketplaceGateway gateway = gateways.get(integracao.getMarketplace());
        if (gateway == null) {
            throw new RegraNegocioException("Marketplace ainda nao possui gateway configurado.");
        }

        try {
            MarketplaceGateway.ResultadoOperacao resultado =
                    gateway.encerrar(integracao, publicacao.getIdentificadorExterno());
            MarketplaceGateway.ResultadoSincronizacao sincronizado =
                    gateway.sincronizar(integracao, publicacao.getIdentificadorExterno());
            publicacao.setStatus(mapearStatus(sincronizado.status()));
            publicacao.setQuantidadePublicada(sincronizado.quantidadeDisponivel());
            if (sincronizado.permalink() != null && !sincronizado.permalink().isBlank()) {
                publicacao.setUrlPublicacao(sincronizado.permalink());
            }
            publicacao.setUltimaSincronizacao(LocalDateTime.now());
            publicacao.setUltimoErro(null);
            PublicacaoMarketplace atualizada = publicacaoRepository.save(publicacao);
            registrarHistorico(integracao, "ENCERRAR_PUBLICACAO", publicacao.getIdentificadorExterno(),
                    StatusHistoricoIntegracaoMarketplace.SUCESSO, null);
            return atualizada;
        } catch (RuntimeException ex) {
            publicacao.setUltimoErro(ex.getMessage());
            publicacao.setUltimaSincronizacao(LocalDateTime.now());
            publicacaoRepository.save(publicacao);
            registrarHistorico(integracao, "ENCERRAR_PUBLICACAO", publicacao.getIdentificadorExterno(),
                    StatusHistoricoIntegracaoMarketplace.FALHA, ex.getMessage());
            throw ex;
        }
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
        java.util.List<StatusPublicacaoMarketplace> statusBloqueadores = java.util.List.of(
                StatusPublicacaoMarketplace.PENDENTE,
                StatusPublicacaoMarketplace.PUBLICADA,
                StatusPublicacaoMarketplace.PAUSADA,
                StatusPublicacaoMarketplace.EM_ANALISE,
                StatusPublicacaoMarketplace.AGUARDANDO_ATIVACAO,
                StatusPublicacaoMarketplace.INATIVA,
                StatusPublicacaoMarketplace.ERRO
        );
        if (publicacaoRepository.existsByProdutoIdAndIntegracaoIdAndStatusIn(
                produtoId, integracaoId, statusBloqueadores)) {
            throw new RegraNegocioException("O produto ja possui uma publicacao ativa ou em processamento nesta integracao.");
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
