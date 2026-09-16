package com.siscomercial.ecommerce.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

/** Item de uma venda externa usado para garantir a baixa única de estoque. */
@Entity
@Table(name = "importacao_item_pedido_marketplace", uniqueConstraints =
        @UniqueConstraint(name = "uk_importacao_item_marketplace", columnNames = {"importacao_id", "identificador_item_externo"}))
@Data
public class ImportacaoItemPedidoMarketplace {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "importacao_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ImportacaoPedidoMarketplace importacao;

    @Column(name = "identificador_item_externo", nullable = false, length = 80)
    private String identificadorItemExterno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(name = "valor_unitario", precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "estoque_baixado", nullable = false)
    private boolean estoqueBaixado;
}
