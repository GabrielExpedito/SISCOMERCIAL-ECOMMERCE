package com.siscomercial.ecommerce.service;

import com.siscomercial.ecommerce.exception.RecursoNaoEncontradoException;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.*;
import com.siscomercial.ecommerce.repository.PedidoRepository;
import com.siscomercial.ecommerce.repository.HistoricoStatusPedidoRepository;
import com.siscomercial.ecommerce.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RF003 - Checkout e Criacao de Pedido.
 * Fluxo: valida estoque -> reserva estoque -> calcula frete/total -> cria pedido
 * com snapshot de precos/endereco (RN025) -> aguarda pagamento.
 */
@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final HistoricoStatusPedidoRepository historicoStatusPedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoService produtoService;
    private final EstoqueService estoqueService;
    private final FreteService freteService;

    @Value("${siscomercial.estoque.reserva-minutos-expiracao:15}")
    private int minutosExpiracaoReserva;

    private final NumeroPedidoService numeroPedidoService;

    public record ItemCarrinho(Long produtoId, int quantidade) {
    }

    /**
     * Cria o pedido a partir do carrinho, aplicando as regras RN021 a RN025.
     */
    @Transactional
    public Pedido criarPedido(Cliente cliente, List<ItemCarrinho> itensCarrinho, Endereco enderecoEntrega,
                              FormaPagamento formaPagamento) {
        if (itensCarrinho == null || itensCarrinho.isEmpty()) {
            throw new RegraNegocioException("O carrinho esta vazio.");
        }

        Pedido pedido = new Pedido();
        pedido.setNumeroPedido(numeroPedidoService.gerar());
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

        Pedido pedidoSalvo = pedidoRepository.save(pedido);
        registrarHistorico(pedidoSalvo, null, StatusPedido.AGUARDANDO_PAGAMENTO,
                OrigemAlteracaoStatusPedido.SISTEMA, null);
        return pedidoSalvo;
    }

    /**
     * Chamado pelo gateway de pagamento (webhook) quando o pagamento e aprovado.
     */
    @Transactional
    public Pedido confirmarPagamentoAprovado(Long pedidoId, String identificadorTransacao) {
        Pedido pedido = buscarPorId(pedidoId);
        pedido.getPagamento().setStatus(StatusPagamento.APROVADO);
        pedido.getPagamento().setIdentificadorTransacao(identificadorTransacao);
        pedido.getPagamento().setDataHoraConfirmacao(LocalDateTime.now());
        alterarStatus(pedido, StatusPedido.PAGAMENTO_APROVADO,
                OrigemAlteracaoStatusPedido.INTEGRACAO, "gateway-pagamento");

        // reserva vira baixa efetiva de estoque (venda)
        for (ItemPedido item : pedido.getItens()) {
            estoqueService.registrarMovimentacao(item.getProduto(), TipoMovimentacaoEstoque.VENDA,
                    item.getQuantidade(), pedido.getId(), "Venda confirmada - pedido " + pedido.getNumeroPedido());
        }
        return pedidoRepository.save(pedido);
    }

    /**
     * RN024 - cancelamento so e livre enquanto o pedido nao teve pagamento aprovado.
     */
    @Transactional
    public Pedido cancelar(Long pedidoId, String motivo) {
        return cancelar(pedidoId, motivo, OrigemAlteracaoStatusPedido.CLIENTE, null);
    }

    @Transactional
    public Pedido cancelar(Long pedidoId, String motivo, OrigemAlteracaoStatusPedido origem, String responsavel) {
        Pedido pedido = buscarPorId(pedidoId);

        if (pedido.getStatus() == StatusPedido.ENTREGUE || pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido " + pedido.getNumeroPedido() + " nao pode ser cancelado (status " +
                    "atual: " + pedido.getStatus() + ").");
        }

        for (ItemPedido item : pedido.getItens()) {
            TipoMovimentacaoEstoque tipo = pedido.getStatus() == StatusPedido.AGUARDANDO_PAGAMENTO
                    ? TipoMovimentacaoEstoque.LIBERACAO_RESERVA
                    : TipoMovimentacaoEstoque.CANCELAMENTO;
            estoqueService.registrarMovimentacao(item.getProduto(), tipo, item.getQuantidade(),
                    pedido.getId(), "Cancelamento pedido " + pedido.getNumeroPedido() + (motivo != null ?
                            " - " + motivo : ""));
        }

        alterarStatus(pedido, StatusPedido.CANCELADO, origem, responsavel);
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
        if (pedido.getStatus() != StatusPedido.EM_SEPARACAO) {
            throw new RegraNegocioException("Pedido " + pedido.getNumeroPedido()
                    + " nao esta apto para faturamento (status atual: " + pedido.getStatus() + ").");
        }
        pedido.setNumeroNotaFiscal(numeroNota);
        pedido.setChaveNotaFiscal(chaveNota);
        alterarStatus(pedido, StatusPedido.FATURADO, OrigemAlteracaoStatusPedido.ADMINISTRADOR, null);
        pedidoRepository.save(pedido);
    }

    /**
     * Executa uma transicao administrativa ou de integracao, rejeitando atalhos no ciclo (RN026).
     */
    @Transactional
    public Pedido alterarStatus(Long pedidoId, StatusPedido novoStatus,
                                OrigemAlteracaoStatusPedido origem, String responsavel) {
        Pedido pedido = buscarPorId(pedidoId);
        alterarStatus(pedido, novoStatus, origem, responsavel);
        return pedidoRepository.save(pedido);
    }

    public List<HistoricoStatusPedido> listarHistorico(Long pedidoId) {
        buscarPorId(pedidoId);
        return historicoStatusPedidoRepository.findByPedidoIdOrderByDataHoraAsc(pedidoId);
    }

    private void alterarStatus(Pedido pedido, StatusPedido novoStatus,
                               OrigemAlteracaoStatusPedido origem, String responsavel) {
        StatusPedido statusAtual = pedido.getStatus();
        if (statusAtual == novoStatus) {
            throw new RegraNegocioException("O pedido ja esta no status " + novoStatus + ".");
        }
        if (!statusAtual.permiteTransicaoPara(novoStatus)) {
            throw new RegraNegocioException("Transicao invalida para o pedido " + pedido.getNumeroPedido()
                    + ": " + statusAtual + " -> " + novoStatus + ".");
        }

        pedido.setStatus(novoStatus);
        registrarHistorico(pedido, statusAtual, novoStatus, origem, responsavel);
    }

    private void registrarHistorico(Pedido pedido, StatusPedido statusAnterior, StatusPedido novoStatus,
                                    OrigemAlteracaoStatusPedido origem, String responsavel) {
        HistoricoStatusPedido historico = new HistoricoStatusPedido();
        historico.setPedido(pedido);
        historico.setStatusAnterior(statusAnterior);
        historico.setNovoStatus(novoStatus);
        historico.setDataHora(LocalDateTime.now());
        historico.setOrigem(origem);
        historico.setResponsavel(responsavel);
        historicoStatusPedidoRepository.save(historico);
    }

    public List<Pedido> listarPedidosDoCliente(Long clienteId) {
        return pedidoRepository.findByClienteIdOrderByDataHoraDesc(clienteId);
    }

    public Page<Pedido> listarPedidosRetaguarda(
            StatusPedido status,
            String numeroPedido,
            String clienteTermo,
            LocalDateTime dataInicio,
            LocalDateTime dataFim,
            Pageable pageable) {

        Specification<Pedido> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (numeroPedido != null && !numeroPedido.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("numeroPedido")), "%" + numeroPedido.trim().toLowerCase() + "%"));
            }

            if (clienteTermo != null && !clienteTermo.isBlank()) {
                String termo = "%" + clienteTermo.trim().toLowerCase() + "%";
                var clienteJoin = root.join("cliente", jakarta.persistence.criteria.JoinType.LEFT);
                Predicate nomeMatch = cb.like(cb.lower(clienteJoin.get("nomeRazaoSocial")), termo);
                Predicate emailMatch = cb.like(cb.lower(clienteJoin.get("email")), termo);
                predicates.add(cb.or(nomeMatch, emailMatch));
            }

            if (dataInicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataHora"), dataInicio));
            }

            if (dataFim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataHora"), dataFim));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return pedidoRepository.findAll(spec, pageable);
    }
}