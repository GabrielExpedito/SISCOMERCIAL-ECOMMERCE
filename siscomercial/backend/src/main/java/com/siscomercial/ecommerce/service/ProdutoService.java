package com.siscomercial.ecommerce.service;

import com.siscomercial.ecommerce.exception.RecursoNaoEncontradoException;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.*;
import com.siscomercial.ecommerce.repository.HistoricoPrecoRepository;
import com.siscomercial.ecommerce.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RF001 - Cadastro e Gestao de Produtos.
 * Concentra as regras RN001 a RN008. E a unica porta de entrada para
 * criar/alterar/inativar produtos - inclusive quando chamada pelo agente de IA.
 */
@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final HistoricoPrecoRepository historicoPrecoRepository;
    private final EstoqueService estoqueService;

    @Transactional
    public Produto criar(Produto produto) {
        // RN001 - codigo interno unico
        if (produtoRepository.existsByCodigoInterno(produto.getCodigoInterno())) {
            throw new RegraNegocioException("RN001: ja existe um produto com o codigo " + produto.getCodigoInterno());
        }
        validarPreco(produto.getPrecoVenda());
        validarEstoqueNaoNegativo(produto.getQuantidadeEstoque());

        if (produto.getImagemPrincipal() == null && !produto.getImagens().isEmpty()) {
            produto.setImagemPrincipal(produto.getImagens().get(0));
        }
        atualizarStatusConformeEstoque(produto);
        produto.setCriadoEm(LocalDateTime.now());
        produto.setAtualizadoEm(LocalDateTime.now());
        return produtoRepository.save(produto);
    }

    @Transactional
    public Produto alterar(Long id, Produto dadosNovos, String usuario) {
        Produto existente = buscarPorId(id);

        validarPreco(dadosNovos.getPrecoVenda());

        // RN007 - alteracao de preco deve ser auditada
        if (existente.getPrecoVenda().compareTo(dadosNovos.getPrecoVenda()) != 0) {
            HistoricoPreco historico = new HistoricoPreco();
            historico.setProdutoId(existente.getId());
            historico.setPrecoAnterior(existente.getPrecoVenda());
            historico.setPrecoNovo(dadosNovos.getPrecoVenda());
            historico.setAlteradoPor(usuario);
            historicoPrecoRepository.save(historico);
        }

        existente.setNome(dadosNovos.getNome());
        existente.setDescricao(dadosNovos.getDescricao());
        existente.setPrecoVenda(dadosNovos.getPrecoVenda());
        existente.setPrecoPromocional(dadosNovos.getPrecoPromocional());
        existente.setCategoria(dadosNovos.getCategoria());
        if (dadosNovos.getImagens() != null && !dadosNovos.getImagens().isEmpty()) {
            existente.setImagens(dadosNovos.getImagens());
        }
        if (dadosNovos.getImagemPrincipal() != null) {
            existente.setImagemPrincipal(dadosNovos.getImagemPrincipal());
        }
        existente.setAtualizadoEm(LocalDateTime.now());
        return produtoRepository.save(existente);
    }

    /** RN008 - produtos ja vendidos nao sao excluidos fisicamente, apenas inativados. */
    @Transactional
    public void inativar(Long id) {
        Produto produto = buscarPorId(id);
        produto.setStatus(StatusProduto.INATIVO);
        produto.setAtualizadoEm(LocalDateTime.now());
        produtoRepository.save(produto);
    }

    @Transactional
    public void reativar(Long id) {
        Produto produto = buscarPorId(id);
        produto.setStatus(StatusProduto.ATIVO);
        atualizarStatusConformeEstoque(produto);
        produtoRepository.save(produto);
    }

    @Transactional
    public void movimentarEstoque(Long produtoId, TipoMovimentacaoEstoque tipo, int quantidade, Long pedidoId, String observacao) {
        Produto produto = buscarPorId(produtoId);
        estoqueService.registrarMovimentacao(produto, tipo, quantidade, pedidoId, observacao);
    }

    public Produto buscarPorId(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto nao encontrado: id " + id));
    }

    public Produto buscarPorCodigo(String codigo) {
        return produtoRepository.findByCodigoInterno(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto nao encontrado: codigo " + codigo));
    }

    public List<Produto> listarTodos() {
        return produtoRepository.findAll();
    }

    /** RN009 - somente produtos ATIVOS aparecem no catalogo/home publicos. */
    public List<Produto> listarCatalogoPublico() {
        return produtoRepository.findByStatus(StatusProduto.ATIVO);
    }

    public List<Produto> pesquisar(String termo) {
        return produtoRepository.findByNomeContainingIgnoreCaseOrCodigoInternoContainingIgnoreCase(termo, termo);
    }

    /** RN011/RN012 - valida quantidade minima (1) e limite de estoque disponivel. */
    public void validarSelecaoQuantidade(Produto produto, int quantidade) {
        if (quantidade < 1) {
            throw new RegraNegocioException("RN011: quantidade minima para aquisicao e 1 unidade.");
        }
        if (produto.getStatus() != StatusProduto.ATIVO) {
            throw new RegraNegocioException("RN004: produto inativo/sem estoque nao pode ser adicionado ao carrinho.");
        }
        if (quantidade > produto.getQuantidadeDisponivel()) {
            throw new RegraNegocioException("RN012: quantidade solicitada excede o estoque disponivel.");
        }
    }

    private void validarPreco(BigDecimal preco) {
        if (preco == null || preco.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("RN002: o preco de venda deve ser maior que zero.");
        }
    }

    private void validarEstoqueNaoNegativo(Integer quantidade) {
        if (quantidade != null && quantidade < 0) {
            throw new RegraNegocioException("RN003: a quantidade em estoque nao pode ser negativa.");
        }
    }

    private void atualizarStatusConformeEstoque(Produto produto) {
        if (produto.getStatus() == StatusProduto.INATIVO) {
            return;
        }
        produto.setStatus(produto.getQuantidadeDisponivel() > 0 ? StatusProduto.ATIVO : StatusProduto.SEM_ESTOQUE);
    }
}
