package com.siscomercial.ecommerce.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusPedidoTest {

    @Test
    void devePermitirApenasProximaEtapaDoFluxoOperacional() {
        assertTrue(StatusPedido.AGUARDANDO_PAGAMENTO.permiteTransicaoPara(StatusPedido.PAGAMENTO_APROVADO));
        assertTrue(StatusPedido.PAGAMENTO_APROVADO.permiteTransicaoPara(StatusPedido.EM_SEPARACAO));
        assertTrue(StatusPedido.EM_SEPARACAO.permiteTransicaoPara(StatusPedido.FATURADO));
        assertTrue(StatusPedido.FATURADO.permiteTransicaoPara(StatusPedido.ENVIADO));
        assertTrue(StatusPedido.ENVIADO.permiteTransicaoPara(StatusPedido.ENTREGUE));
    }

    @Test
    void deveRejeitarAtalhosERetornoDeEtapa() {
        assertFalse(StatusPedido.AGUARDANDO_PAGAMENTO.permiteTransicaoPara(StatusPedido.EM_SEPARACAO));
        assertFalse(StatusPedido.FATURADO.permiteTransicaoPara(StatusPedido.EM_SEPARACAO));
        assertFalse(StatusPedido.PAGAMENTO_RECUSADO.permiteTransicaoPara(StatusPedido.EM_SEPARACAO));
        assertFalse(StatusPedido.ENTREGUE.permiteTransicaoPara(StatusPedido.CANCELADO));
    }
}
