package com.siscomercial.ecommerce.controller;

import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.HistoricoStatusPedido;
import com.siscomercial.ecommerce.model.OrigemAlteracaoStatusPedido;
import com.siscomercial.ecommerce.model.Pedido;
import com.siscomercial.ecommerce.model.StatusPedido;
import com.siscomercial.ecommerce.model.DTO.RetaguardaDTOs.*;
import com.siscomercial.ecommerce.service.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RF006 - Retaguarda Administrativa (secao 14 da especificacao).
 * Endpoints protegidos por ROLE_ADMIN no SecurityConfig.
 * Opera exclusivamente com DTOs desacoplados do JPA.
 */
@RestController
@RequestMapping("/api/retaguarda/pedidos")
@RequiredArgsConstructor
public class RetaguardaPedidoController {

    private final PedidoService pedidoService;

    /**
     * Listagem paginada de pedidos com filtros de status, numero, cliente e periodo.
     */
    @GetMapping
    public Page<RetaguardaPedidoResumoDTO> listarPedidos(
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) String numeroPedido,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataFim,
            @PageableDefault(size = 20, sort = "dataHora", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<Pedido> pedidos = pedidoService.listarPedidosRetaguarda(
                status,
                numeroPedido,
                cliente,
                dataInicio,
                dataFim,
                pageable
        );

        return pedidos.map(this::mapearParaResumo);
    }

    /**
     * Detalhes completos do pedido para inspecao operacional.
     */
    @GetMapping("/{id}")
    public RetaguardaPedidoDetalheDTO detalhePedido(@PathVariable Long id) {
        Pedido pedido = pedidoService.buscarPorId(id);
        return mapearParaDetalhe(pedido);
    }

    /**
     * Historico de alteracoes de status para auditoria e rastreabilidade.
     */
    @GetMapping("/{id}/historico")
    public List<HistoricoStatusDTO> listarHistorico(@PathVariable Long id) {
        List<HistoricoStatusPedido> historico = pedidoService.listarHistorico(id);
        return historico.stream()
                .map(h -> new HistoricoStatusDTO(
                        h.getId(),
                        h.getStatusAnterior(),
                        h.getNovoStatus(),
                        h.getDataHora(),
                        h.getOrigem(),
                        h.getResponsavel()
                ))
                .toList();
    }

    /**
     * Avanco de status da retaguarda administrativa (RN026, RN030, RN036).
     */
    @PostMapping("/{id}/status")
    public RetaguardaPedidoDetalheDTO alterarStatus(
            @PathVariable Long id,
            @RequestBody AlterarStatusAdminRequest request,
            Authentication authentication
    ) {
        if (request == null || request.status() == null) {
            throw new RegraNegocioException("O novo status do pedido e obrigatorio.");
        }

        String responsavel = extrairResponsavel(authentication);
        Pedido pedido = pedidoService.alterarStatus(
                id,
                request.status(),
                OrigemAlteracaoStatusPedido.ADMINISTRADOR,
                responsavel
        );

        return mapearParaDetalhe(pedido);
    }

    /**
     * Cancelamento de pedido pela retaguarda com liberacao ou devolucao de estoque (RN031).
     */
    @PostMapping("/{id}/cancelar")
    public RetaguardaPedidoDetalheDTO cancelarPedido(
            @PathVariable Long id,
            @RequestBody(required = false) CancelarPedidoAdminRequest request,
            Authentication authentication
    ) {
        String motivo = request != null ? request.motivo() : null;
        String responsavel = extrairResponsavel(authentication);

        Pedido pedido = pedidoService.cancelar(
                id,
                motivo,
                OrigemAlteracaoStatusPedido.ADMINISTRADOR,
                responsavel
        );

        return mapearParaDetalhe(pedido);
    }

    private String extrairResponsavel(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
            return oidcUser.getEmail();
        }
        return authentication != null ? authentication.getName() : "ADMINISTRADOR";
    }

    private RetaguardaPedidoResumoDTO mapearParaResumo(Pedido pedido) {
        var cliente = pedido.getCliente();
        return new RetaguardaPedidoResumoDTO(
                pedido.getId(),
                pedido.getNumeroPedido(),
                pedido.getDataHora(),
                cliente != null ? cliente.getId() : null,
                cliente != null ? cliente.getNomeRazaoSocial() : null,
                cliente != null ? cliente.getEmail() : null,
                pedido.getStatus(),
                pedido.getTotal(),
                pedido.getItens() != null ? pedido.getItens().size() : 0,
                pedido.getModalidadeFrete()
        );
    }

    private RetaguardaPedidoDetalheDTO mapearParaDetalhe(Pedido pedido) {
        var cliente = pedido.getCliente();
        ClienteResumoDTO clienteDTO = cliente != null ? new ClienteResumoDTO(
                cliente.getId(),
                cliente.getNomeRazaoSocial(),
                cliente.getEmail(),
                cliente.getCpfCnpj(),
                cliente.getTelefone()
        ) : null;

        EnderecoDTO enderecoDTO = new EnderecoDTO(
                pedido.getEnderecoCep(),
                pedido.getEnderecoLogradouro(),
                pedido.getEnderecoNumero(),
                pedido.getEnderecoComplemento(),
                pedido.getEnderecoBairro(),
                pedido.getEnderecoCidade(),
                pedido.getEnderecoEstado()
        );

        List<ItemPedidoDTO> itensDTO = pedido.getItens() != null
                ? pedido.getItens().stream().map(item -> new ItemPedidoDTO(
                        item.getId(),
                        item.getProduto() != null ? item.getProduto().getId() : null,
                        item.getProduto() != null ? item.getProduto().getCodigoInterno() : null,
                        item.getProduto() != null ? item.getProduto().getNome() : null,
                        item.getDescricaoMomentoCompra(),
                        item.getQuantidade(),
                        item.getValorUnitario(),
                        item.getDesconto(),
                        item.getSubtotal()
                )).toList()
                : List.of();

        PagamentoDTO pagamentoDTO = pedido.getPagamento() != null ? new PagamentoDTO(
                pedido.getPagamento().getId(),
                pedido.getPagamento().getFormaPagamento(),
                pedido.getPagamento().getValor(),
                pedido.getPagamento().getIdentificadorTransacao(),
                pedido.getPagamento().getStatus(),
                pedido.getPagamento().getDataHoraTentativa(),
                pedido.getPagamento().getDataHoraConfirmacao()
        ) : null;

        return new RetaguardaPedidoDetalheDTO(
                pedido.getId(),
                pedido.getNumeroPedido(),
                pedido.getDataHora(),
                pedido.getStatus(),
                pedido.getSubtotal(),
                pedido.getDesconto(),
                pedido.getFrete(),
                pedido.getTotal(),
                enderecoDTO,
                pedido.getModalidadeFrete(),
                pedido.getPrazoEstimadoDias(),
                pedido.getCodigoRastreamento(),
                pedido.getReservaExpiraEm(),
                pedido.getNumeroNotaFiscal(),
                pedido.getChaveNotaFiscal(),
                clienteDTO,
                itensDTO,
                pagamentoDTO
        );
    }
}