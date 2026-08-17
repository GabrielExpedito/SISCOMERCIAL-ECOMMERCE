package com.siscomercial.ecommerce.model;

/** RF003 - 7.11 Status do pedido */
public enum StatusPedido {
    AGUARDANDO_PAGAMENTO,
    PAGAMENTO_APROVADO,
    EM_SEPARACAO,
    FATURADO,
    ENVIADO,
    ENTREGUE,
    CANCELADO,
    PAGAMENTO_RECUSADO,
    EXPIRADO,
    DEVOLVIDO
}
