package com.siscomercial.ecommerce.service;

import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.*;
import com.siscomercial.ecommerce.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PedidoServiceEnderecoOwnershipTest {
    private PedidoRepository pedidos;
    private ProdutoRepository produtos;
    private ClienteRepository clientes;
    private ProdutoService produtoService;
    private EstoqueService estoqueService;
    private NumeroPedidoService numeroPedidoService;
    private PedidoService service;
    private Cliente cliente;
    private Endereco endereco;
    private OidcUser usuario;

    @BeforeEach
    void setUp() {
        pedidos = mock(PedidoRepository.class);
        produtos = mock(ProdutoRepository.class);
        clientes = mock(ClienteRepository.class);
        produtoService = mock(ProdutoService.class);
        estoqueService = mock(EstoqueService.class);
        numeroPedidoService = mock(NumeroPedidoService.class);
        service = new PedidoService(pedidos, mock(HistoricoStatusPedidoRepository.class), produtos,
                produtoService, estoqueService, new FreteService(), numeroPedidoService, clientes);

        cliente = new Cliente();
        cliente.setId(10L);
        cliente.setGoogleId("cliente-a-sub");
        cliente.setPerfil(PerfilUsuario.CLIENTE);
        cliente.setEnderecos(new ArrayList<>());
        endereco = new Endereco();
        endereco.setId(20L);
        endereco.setCep("01001-000");
        endereco.setLogradouro("Rua A");
        endereco.setCliente(cliente);
        cliente.getEnderecos().add(endereco);
        usuario = mock(OidcUser.class);
        when(usuario.getSubject()).thenReturn("cliente-a-sub");
        when(clientes.findByGoogleId("cliente-a-sub")).thenReturn(Optional.of(cliente));
    }

    @Test
    void checkoutComEnderecoDoProprioClienteCriaPedidoComProprietarioAutenticado() {
        configurarProdutoEPedido();

        Pedido pedido = service.criarPedido(usuario, List.of(new PedidoService.ItemCarrinho(5L, 1)), endereco,
                FormaPagamento.PIX);

        assertSame(cliente, pedido.getCliente());
        assertEquals("01001-000", pedido.getEnderecoCep());
        verify(pedidos).save(any(Pedido.class));
    }

    @Test
    void checkoutPersisteEnderecoNovoSobProprietarioAutenticado() {
        configurarProdutoEPedido();
        when(clientes.save(cliente)).thenAnswer(inv -> {
            cliente.getEnderecos().get(1).setId(21L);
            return cliente;
        });
        Endereco novoEndereco = new Endereco();
        novoEndereco.setCep("01001-000");
        novoEndereco.setLogradouro("Rua Nova");
        novoEndereco.setId(null);
        novoEndereco.setPrincipal(false);
        novoEndereco.setCliente(new Cliente()); // tentativa de proprietário no corpo

        Pedido pedido = service.criarPedido(usuario, List.of(new PedidoService.ItemCarrinho(5L, 1)), novoEndereco,
                FormaPagamento.PIX);

        assertSame(cliente, pedido.getCliente());
        assertEquals("Rua Nova", pedido.getEnderecoLogradouro());
        assertEquals(21L, cliente.getEnderecos().get(1).getId());
        assertSame(cliente, cliente.getEnderecos().get(1).getCliente());
        verify(clientes).save(cliente);
    }

    @Test
    void checkoutComEnderecoDeOutroClienteFalhaAntesDeCriarPedido() {
        Endereco enderecoAlheio = new Endereco();
        enderecoAlheio.setId(99L);
        enderecoAlheio.setCep("99999-999");
        enderecoAlheio.setCliente(new Cliente());

        assertThrows(RegraNegocioException.class,
                () -> service.criarPedido(usuario, List.of(new PedidoService.ItemCarrinho(5L, 1)), enderecoAlheio,
                        FormaPagamento.PIX));
        verify(pedidos, never()).save(any(Pedido.class));
        verify(produtos, never()).findById(any());
    }

    private void configurarProdutoEPedido() {
        Produto produto = new Produto();
        produto.setId(5L);
        produto.setDescricao("Produto");
        produto.setPrecoVenda(new BigDecimal("12.50"));
        produto.setStatus(StatusProduto.ATIVO);
        produto.setQuantidadeEstoque(4);
        when(produtos.findById(5L)).thenReturn(Optional.of(produto));
        when(numeroPedidoService.gerar()).thenReturn("P-1");
        when(pedidos.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
    }
}
