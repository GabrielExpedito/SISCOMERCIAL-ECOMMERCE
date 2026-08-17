package com.siscomercial.ecommerce.controller;

import com.siscomercial.ecommerce.model.*;
import com.siscomercial.ecommerce.service.ClienteService;
import com.siscomercial.ecommerce.service.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** RF003 - Checkout e Criacao de Pedido. */
@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;
    private final ClienteService clienteService;

    public record CheckoutRequest(
            Long clienteId,
            List<PedidoService.ItemCarrinho> itens,
            Endereco enderecoEntrega,
            FormaPagamento formaPagamento
    ) {}

    @PostMapping("/checkout")
    public Pedido checkout(@RequestBody CheckoutRequest request) {
        Cliente cliente = clienteService.buscarPorId(request.clienteId());
        return pedidoService.criarPedido(cliente, request.itens(), request.enderecoEntrega(), request.formaPagamento());
    }

    @GetMapping("/{numeroPedido}")
    public Pedido buscar(@PathVariable String numeroPedido) {
        return pedidoService.buscarPorNumero(numeroPedido);
    }

    /** Simula o webhook do gateway de pagamento aprovando o pagamento (gateway real: "a definir"). */
    @PostMapping("/{id}/confirmar-pagamento")
    public Pedido confirmarPagamento(@PathVariable Long id, @RequestParam(defaultValue = "TXN-SIMULADA") String transacaoId) {
        return pedidoService.confirmarPagamentoAprovado(id, transacaoId);
    }

    @PostMapping("/{id}/cancelar")
    public Pedido cancelar(@PathVariable Long id, @RequestParam(required = false) String motivo) {
        return pedidoService.cancelar(id, motivo);
    }
}
