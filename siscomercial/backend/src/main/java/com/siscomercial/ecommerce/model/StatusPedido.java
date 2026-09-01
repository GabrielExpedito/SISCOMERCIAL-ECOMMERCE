package com.siscomercial.ecommerce.model;

import java.util.EnumSet;
import java.util.Set;

/** Estados e transicoes permitidas do ciclo do pedido (RF004, RN026). */
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
    DEVOLVIDO;

    public boolean permiteTransicaoPara(StatusPedido destino) {
        return transicoesPermitidas().contains(destino);
    }

    public Set<StatusPedido> transicoesPermitidas() {
        return switch (this) {
            case AGUARDANDO_PAGAMENTO -> EnumSet.of(PAGAMENTO_APROVADO, PAGAMENTO_RECUSADO, EXPIRADO, CANCELADO);
            case PAGAMENTO_APROVADO -> EnumSet.of(EM_SEPARACAO, CANCELADO);
            case EM_SEPARACAO -> EnumSet.of(FATURADO, CANCELADO);
            case FATURADO -> EnumSet.of(ENVIADO);
            case ENVIADO -> EnumSet.of(ENTREGUE);
            case ENTREGUE -> EnumSet.of(DEVOLVIDO);
            case PAGAMENTO_RECUSADO, EXPIRADO, CANCELADO, DEVOLVIDO -> EnumSet.noneOf(StatusPedido.class);
        };
    }
}
