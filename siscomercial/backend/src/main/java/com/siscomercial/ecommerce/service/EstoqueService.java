package com.siscomercial.ecommerce.service;

import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.MovimentacaoEstoque;
import com.siscomercial.ecommerce.model.Produto;
import com.siscomercial.ecommerce.model.StatusProduto;
import com.siscomercial.ecommerce.model.TipoMovimentacaoEstoque;
import com.siscomercial.ecommerce.repository.MovimentacaoEstoqueRepository;
import com.siscomercial.ecommerce.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF001 - 5.5 Controle de estoque por movimentacoes.
 * Toda alteracao de estoque passa por aqui, garantindo RN003 (nunca negativo)
 * e o registro do historico operacional (auditoria).
 */
@Service
@RequiredArgsConstructor
public class EstoqueService {

    private final ProdutoRepository produtoRepository;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;

    @Transactional
    public void registrarMovimentacao(Produto produto, TipoMovimentacaoEstoque tipo, int quantidade, Long pedidoId, String observacao) {
        switch (tipo) {
            case ENTRADA, CANCELAMENTO, LIBERACAO_RESERVA -> produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() + quantidade);
            case VENDA -> aplicarSaida(produto, quantidade);
            case RESERVA -> aplicarReserva(produto, quantidade);
            case AJUSTE -> aplicarAjuste(produto, quantidade);
        }

        atualizarStatusPorEstoque(produto);
        produtoRepository.save(produto);

        MovimentacaoEstoque mov = new MovimentacaoEstoque();
        mov.setProdutoId(produto.getId());
        mov.setTipo(tipo);
        mov.setQuantidade(quantidade);
        mov.setPedidoId(pedidoId);
        mov.setObservacao(observacao);
        movimentacaoRepository.save(mov);
    }

    private void aplicarSaida(Produto produto, int quantidade) {
        // RN003: quantidade disponivel nunca pode ficar negativa
        if (produto.getQuantidadeEstoque() - quantidade < 0) {
            throw new RegraNegocioException("RN003: estoque insuficiente para o produto " + produto.getCodigoInterno());
        }
        produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() - quantidade);
        // libera a reserva correspondente, se houver
        int reservada = Math.max(0, produto.getQuantidadeReservada() - quantidade);
        produto.setQuantidadeReservada(reservada);
    }

    private void aplicarReserva(Produto produto, int quantidade) {
        // RN012/RN021: nao reservar alem do disponivel
        if (produto.getQuantidadeDisponivel() < quantidade) {
            throw new RegraNegocioException("RN021/RN022: estoque insuficiente para reservar o produto " + produto.getCodigoInterno());
        }
        produto.setQuantidadeReservada(produto.getQuantidadeReservada() + quantidade);
    }

    private void aplicarAjuste(Produto produto, int quantidadeDelta) {
        int novoValor = produto.getQuantidadeEstoque() + quantidadeDelta;
        if (novoValor < 0) {
            throw new RegraNegocioException("RN003: ajuste deixaria o estoque negativo para o produto " + produto.getCodigoInterno());
        }
        produto.setQuantidadeEstoque(novoValor);
    }

    /** Mantem o status SEM_ESTOQUE sincronizado com a quantidade disponivel (RF001 5.2). */
    private void atualizarStatusPorEstoque(Produto produto) {
        if (produto.getStatus() == StatusProduto.INATIVO) {
            return;
        }
        if (produto.getQuantidadeDisponivel() <= 0) {
            produto.setStatus(StatusProduto.SEM_ESTOQUE);
        } else if (produto.getStatus() == StatusProduto.SEM_ESTOQUE) {
            produto.setStatus(StatusProduto.ATIVO);
        }
    }
}
