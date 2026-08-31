package com.siscomercial.ecommerce.controller;

import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.Cliente;
import com.siscomercial.ecommerce.model.Endereco;
import com.siscomercial.ecommerce.model.FormaPagamento;
import com.siscomercial.ecommerce.model.Pedido;
import com.siscomercial.ecommerce.model.PerfilUsuario;
import com.siscomercial.ecommerce.service.ClienteService;
import com.siscomercial.ecommerce.service.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;
    private final ClienteService clienteService;

    public record CheckoutRequest(
            List<PedidoService.ItemCarrinho> itens,
            Endereco enderecoEntrega,
            FormaPagamento formaPagamento
    ) {}

    /**
     * Cria um pedido para o cliente autenticado.
     */
    @PostMapping("/checkout")
    public Pedido checkout(
            @RequestBody CheckoutRequest request,
            Authentication authentication) {

        Cliente cliente =
                obterClienteAutenticado(authentication);

        return pedidoService.criarPedido(
                cliente,
                request.itens(),
                request.enderecoEntrega(),
                request.formaPagamento()
        );
    }

    /**
     * Lista os pedidos do cliente autenticado.
     */
    @GetMapping("/meus")
    public List<Pedido> meusPedidos(
            Authentication authentication) {

        Cliente cliente =
                obterClienteAutenticado(authentication);

        return pedidoService.listarPedidosDoCliente(
                cliente.getId()
        );
    }

    /**
     * Busca um pedido pelo numero publico.
     */
    @GetMapping("/{numeroPedido}")
    public Pedido buscar(
            @PathVariable String numeroPedido,
            Authentication authentication) {

        Cliente cliente =
                obterClienteAutenticado(authentication);

        Pedido pedido =
                pedidoService.buscarPorNumero(numeroPedido);

        boolean ehDono =
                pedido.getCliente()
                        .getId()
                        .equals(cliente.getId());

        boolean ehAdmin =
                cliente.getPerfil()
                        == PerfilUsuario.ADMIN;

        if (!ehDono && !ehAdmin) {

            throw new RegraNegocioException(
                    "Voce nao possui permissao para visualizar este pedido."
            );
        }

        return pedido;
    }

    /**
     * Simula aprovacao do pagamento.
     * Posteriormente sera substituido pelo webhook
     * do gateway de pagamento.
     */
    @PostMapping("/{id}/confirmar-pagamento")
    public Pedido confirmarPagamento(
            @PathVariable Long id,
            @RequestParam(
                    defaultValue = "TXN-SIMULADA"
            ) String transacaoId) {

        return pedidoService
                .confirmarPagamentoAprovado(
                        id,
                        transacaoId
                );
    }

    /**
     * Cancela um pedido.
     */
    @PostMapping("/{id}/cancelar")
    public Pedido cancelar(
            @PathVariable Long id,
            @RequestParam(required = false) String motivo,
            Authentication authentication) {

        Cliente cliente =
                obterClienteAutenticado(authentication);

        Pedido pedido =
                pedidoService.buscarPorId(id);

        boolean ehDono =
                pedido.getCliente()
                        .getId()
                        .equals(cliente.getId());

        boolean ehAdmin =
                cliente.getPerfil()
                        == PerfilUsuario.ADMIN;

        if (!ehDono && !ehAdmin) {

            throw new RegraNegocioException(
                    "Voce nao possui permissao para cancelar este pedido."
            );
        }

        return pedidoService.cancelar(
                id,
                motivo
        );
    }

    /**
     * Recupera o cliente correspondente ao usuario
     * autenticado pelo Google.
     */
    private Cliente obterClienteAutenticado(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new RegraNegocioException(
                    "Usuario nao autenticado."
            );
        }

        Object principal =
                authentication.getPrincipal();

        if (!(principal instanceof OidcUser oidcUser)) {

            throw new RegraNegocioException(
                    "Usuario autenticado nao possui uma conta OIDC valida."
            );
        }

        String email = oidcUser.getEmail();

        if (email == null || email.isBlank()) {

            throw new RegraNegocioException(
                    "Nao foi possivel identificar o e-mail do usuario autenticado."
            );
        }

        return clienteService.buscarPorEmail(email);
    }
}