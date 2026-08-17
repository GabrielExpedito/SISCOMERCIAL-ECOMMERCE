package com.siscomercial.ecommerce.service;

import com.siscomercial.ecommerce.exception.RecursoNaoEncontradoException;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.*;
import com.siscomercial.ecommerce.repository.PedidoRepository;
import com.siscomercial.ecommerce.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RF003 - Checkout e Criacao de Pedido.
 * Fluxo: valida estoque -> reserva estoque -> calcula frete/total -> cria pedido
 * com snapshot de precos/endereco (RN025) -> aguarda pagamento.
 */
@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoService produtoService;
    private final EstoqueService estoqueService;
    private final FreteService freteService;

    @Value("${siscomercial.estoque.reserva-minutos-expiracao:15}")
    private int minutosExpiracaoReserva;

    private final AtomicInteger sequenciaPedido = new AtomicInteger(1);

    public record ItemCarrinho(Long produtoId, int quantidade) {}

    /**
     * Cria o pedido a partir do carrinho, aplicando as regras RN021 a RN025.
     */
    @Transactional
    public Pedido criarPedido(Cliente cliente, List<ItemCarrinho> itensCarrinho, Endereco enderecoEntrega, FormaPagamento formaPagamento) {
        if (itensCarrinho == null || itensCarrinho.isEmpty()) {
            throw new RegraNegocioException("O carrinho esta vazio.");
        }

        Pedido pedido = new Pedido();
        pedido.setNumeroPedido(gerarNumeroPedido());
        pedido.setCliente(cliente);
        pedido.setDataHora(LocalDateTime.now());

        // snapshot do endereco (RN025)
        pedido.setEnderecoCep(enderecoEntrega.getCep());
        pedido.setEnderecoLogradouro(enderecoEntrega.getLogradouro());
        pedido.setEnderecoNumero(enderecoEntrega.getNumero());
        pedido.setEnderecoComplemento(enderecoEntrega.getComplemento());
        pedido.setEnderecoBairro(enderecoEntrega.getBairro());
        pedido.setEnderecoCidade(enderecoEntrega.getCidade());
        pedido.setEnderecoEstado(enderecoEntrega.getEstado());

        BigDecimal subtotal = BigDecimal.ZERO;

        for (ItemCarrinho itemCarrinho : itensCarrinho) {
            Produto produto = produtoRepository.findById(itemCarrinho.produtoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Produto nao encontrado: id " + itemCarrinho.produtoId()));

            // RN021/RN013 - validacao final de estoque imediatamente antes da confirmacao
            produtoService.validarSelecaoQuantidade(produto, itemCarrinho.quantidade());

            ItemPedido item = new ItemPedido();
            item.setPedido(pedido);
            item.setProduto(produto);
            item.setDescricaoMomentoCompra(produto.getDescricao()); // RN025 - snapshot
            item.setQuantidade(itemCarrinho.quantidade());
            BigDecimal precoUnitario = produto.isEmPromocao() ? produto.getPrecoPromocional() : produto.getPrecoVenda();
            item.setValorUnitario(precoUnitario); // RN025 - preco congelado no pedido
            BigDecimal subtotalItem = precoUnitario.multiply(BigDecimal.valueOf(itemCarrinho.quantidade()));
            item.setSubtotal(subtotalItem);

            pedido.getItens().add(item);
            subtotal = subtotal.add(subtotalItem);

            // RN023 - reserva de estoque durante o pagamento (RESERVA, nao baixa ainda)
            estoqueService.registrarMovimentacao(produto, TipoMovimentacaoEstoque.RESERVA,
                    itemCarrinho.quantidade(), null, "Reserva para pedido " + pedido.getNumeroPedido());
        }

        FreteService.CotacaoFrete cotacao = freteService.calcular(enderecoEntrega.getCep());
        pedido.setModalidadeFrete(cotacao.modalidade());
        pedido.setPrazoEstimadoDias(cotacao.prazoDias());
        pedido.setFrete(cotacao.valor());

        pedido.setSubtotal(subtotal);
        pedido.setDesconto(BigDecimal.ZERO);
        pedido.setTotal(subtotal.add(pedido.getFrete()).subtract(pedido.getDesconto()));
        pedido.setStatus(StatusPedido.AGUARDANDO_PAGAMENTO);
        // RN023 - expiracao de reserva parametrizavel
        pedido.setReservaExpiraEm(LocalDateTime.now().plusMinutes(minutosExpiracaoReserva));

        Pagamento pagamento = new Pagamento();
        pagamento.setPedido(pedido);
        pagamento.setFormaPagamento(formaPagamento);
        pagamento.setValor(pedido.getTotal());
        pagamento.setStatus(StatusPagamento.PENDENTE);
        pedido.setPagamento(pagamento);

        return pedidoRepository.save(pedido);
    }

    /** Chamado pelo gateway de pagamento (webhook) quando o pagamento e aprovado. */
    @Transactional
    public Pedido confirmarPagamentoAprovado(Long pedidoId, String identificadorTransacao) {
        Pedido pedido = buscarPorId(pedidoId);
        pedido.getPagamento().setStatus(StatusPagamento.APROVADO);
        pedido.getPagamento().setIdentificadorTransacao(identificadorTransacao);
        pedido.getPagamento().setDataHoraConfirmacao(LocalDateTime.now());
        pedido.setStatus(StatusPedido.PAGAMENTO_APROVADO);

        // reserva vira baixa efetiva de estoque (venda)
        for (ItemPedido item : pedido.getItens()) {
            estoqueService.registrarMovimentacao(item.getProduto(), TipoMovimentacaoEstoque.VENDA,
                    item.getQuantidade(), pedido.getId(), "Venda confirmada - pedido " + pedido.getNumeroPedido());
        }
        return pedidoRepository.save(pedido);
    }

    /** RN024 - cancelamento so e livre enquanto o pedido nao teve pagamento aprovado. */
    @Transactional
    public Pedido cancelar(Long pedidoId, String motivo) {
        Pedido pedido = buscarPorId(pedidoId);

        if (pedido.getStatus() == StatusPedido.ENTREGUE || pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido " + pedido.getNumeroPedido() + " nao pode ser cancelado (status atual: " + pedido.getStatus() + ").");
        }

        for (ItemPedido item : pedido.getItens()) {
            TipoMovimentacaoEstoque tipo = pedido.getStatus() == StatusPedido.AGUARDANDO_PAGAMENTO
                    ? TipoMovimentacaoEstoque.LIBERACAO_RESERVA
                    : TipoMovimentacaoEstoque.CANCELAMENTO;
            estoqueService.registrarMovimentacao(item.getProduto(), tipo, item.getQuantidade(),
                    pedido.getId(), "Cancelamento pedido " + pedido.getNumeroPedido() + (motivo != null ? " - " + motivo : ""));
        }

        pedido.setStatus(StatusPedido.CANCELADO);
        return pedidoRepository.save(pedido);
    }

    public Pedido buscarPorId(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido nao encontrado: id " + id));
    }

    public Pedido buscarPorNumero(String numeroPedido) {
        return pedidoRepository.findByNumeroPedido(numeroPedido)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido nao encontrado: " + numeroPedido));
    }

    @Transactional
    public void registrarFaturamento(Long pedidoId, String numeroNota, String chaveNota) {
        Pedido pedido = buscarPorId(pedidoId);
        pedido.setNumeroNotaFiscal(numeroNota);
        pedido.setChaveNotaFiscal(chaveNota);
        pedido.setStatus(StatusPedido.FATURADO);
        pedidoRepository.save(pedido);
    }

    private String gerarNumeroPedido() {
        int seq = sequenciaPedido.getAndIncrement();
        return "SIS-" + Year.now() + "-" + String.format("%06d", seq + pedidoRepository.count());
    }
}
