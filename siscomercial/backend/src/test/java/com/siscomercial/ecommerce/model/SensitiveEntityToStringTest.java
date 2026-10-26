package com.siscomercial.ecommerce.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SensitiveEntityToStringTest {
    @Test
    void clienteToStringOmiteDadosPessoaisECredenciais() {
        Cliente cliente = new Cliente();
        cliente.setNomeRazaoSocial("nome-ficticio");
        cliente.setCpfCnpj("cpf-ficticio");
        cliente.setEmail("email-ficticio@example.test");
        cliente.setTelefone("telefone-ficticio");
        cliente.setSenhaHash("hash-ficticio");
        cliente.setGoogleId("google-id-ficticio");

        String value = cliente.toString();
        assertFalse(value.contains("nome-ficticio"));
        assertFalse(value.contains("cpf-ficticio"));
        assertFalse(value.contains("email-ficticio"));
        assertFalse(value.contains("telefone-ficticio"));
        assertFalse(value.contains("hash-ficticio"));
        assertFalse(value.contains("google-id-ficticio"));
    }

    @Test
    void integracaoToStringOmiteTokensSegredosEOAuthState() {
        IntegracaoMarketplace integracao = new IntegracaoMarketplace();
        integracao.setCredenciaisProtegidas("refresh-token-ficticio");
        integracao.setTokenProtegido("access-token-ficticio");
        integracao.setOauthState("oauth-state-ficticio");

        String value = integracao.toString();
        assertFalse(value.contains("refresh-token-ficticio"));
        assertFalse(value.contains("access-token-ficticio"));
        assertFalse(value.contains("oauth-state-ficticio"));
    }

    @Test
    void enderecoPedidoEPagamentoOmitToStringComDadosPessoaisEFinanceiros() {
        Endereco endereco = new Endereco();
        endereco.setCep("cep-ficticio");
        endereco.setLogradouro("rua-ficticia");
        endereco.setNumero("numero-ficticio");
        endereco.setCidade("cidade-ficticia");
        endereco.setEstado("estado-ficticio");
        String enderecoText = endereco.toString();
        assertFalse(enderecoText.contains("cep-ficticio"));
        assertFalse(enderecoText.contains("rua-ficticia"));
        assertFalse(enderecoText.contains("numero-ficticio"));
        assertFalse(enderecoText.contains("cidade-ficticia"));
        assertFalse(enderecoText.contains("estado-ficticio"));

        Pedido pedido = new Pedido();
        pedido.setNumeroPedido("pedido-ficticio");
        pedido.setEnderecoCep("cep-pedido-ficticio");
        pedido.setCodigoRastreamento("rastreio-ficticio");
        pedido.setChaveNotaFiscal("chave-fiscal-ficticia");
        String pedidoText = pedido.toString();
        assertFalse(pedidoText.contains("pedido-ficticio"));
        assertFalse(pedidoText.contains("cep-pedido-ficticio"));
        assertFalse(pedidoText.contains("rastreio-ficticio"));
        assertFalse(pedidoText.contains("chave-fiscal-ficticia"));

        Pagamento pagamento = new Pagamento();
        pagamento.setIdentificadorTransacao("transacao-ficticia");
        assertFalse(pagamento.toString().contains("transacao-ficticia"));
    }
}
