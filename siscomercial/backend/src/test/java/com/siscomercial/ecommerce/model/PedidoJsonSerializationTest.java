package com.siscomercial.ecommerce.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PedidoJsonSerializationTest {

    @Test
    void deveSerializarPedidoSemCicloInfinito() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        Cliente cliente = new Cliente();
        cliente.setId(10L);
        cliente.setNomeRazaoSocial("Cliente Teste");
        cliente.setEmail("teste@exemplo.com");
        cliente.setSenhaHash("$2a$10$hashedPasswordHere");

        Endereco endereco = new Endereco();
        endereco.setId(20L);
        endereco.setCep("01001000");
        endereco.setLogradouro("Praça da Sé");
        endereco.setCliente(cliente);

        cliente.getEnderecos().add(endereco);

        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setNumeroPedido("SIS-2026-000001");
        pedido.setDataHora(LocalDateTime.now());
        pedido.setCliente(cliente);
        pedido.setStatus(StatusPedido.AGUARDANDO_PAGAMENTO);
        pedido.setSubtotal(new BigDecimal("100.00"));
        pedido.setFrete(new BigDecimal("15.00"));
        pedido.setTotal(new BigDecimal("115.00"));

        Produto produto = new Produto();
        produto.setId(50L);
        produto.setCodigoInterno("PROD-1");
        produto.setNome("Teclado Mecânico");
        produto.setPrecoVenda(new BigDecimal("100.00"));

        ItemPedido item = new ItemPedido();
        item.setId(30L);
        item.setPedido(pedido);
        item.setProduto(produto);
        item.setQuantidade(1);
        item.setValorUnitario(new BigDecimal("100.00"));
        item.setSubtotal(new BigDecimal("100.00"));

        pedido.getItens().add(item);

        Pagamento pagamento = new Pagamento();
        pagamento.setId(40L);
        pagamento.setPedido(pedido);
        pagamento.setFormaPagamento(FormaPagamento.PIX);
        pagamento.setValor(pedido.getTotal());
        pagamento.setStatus(StatusPagamento.PENDENTE);

        pedido.setPagamento(pagamento);

        String json = mapper.writeValueAsString(pedido);

        assertNotNull(json);
        assertTrue(json.contains("SIS-2026-000001"));
        assertFalse(json.contains("senhaHash"), "senhaHash não deve ser serializada");
        assertFalse(json.contains("hashedPasswordHere"), "Hash da senha não deve constar no JSON");

        // Validar que o JSON é válido e parseável
        var tree = mapper.readTree(json);
        assertEquals("SIS-2026-000001", tree.get("numeroPedido").asText());
        assertEquals(1, tree.get("itens").size());
        assertNull(tree.get("itens").get(0).get("pedido"), "ItemPedido não deve conter referência de volta para pedido");
        assertNull(tree.get("pagamento").get("pedido"), "Pagamento não deve conter referência de volta para pedido");
        assertNull(tree.get("cliente").get("enderecos").get(0).get("cliente"), "Endereco não deve conter referência de volta para cliente");
    }
}
