package com.siscomercial.ecommerce.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/** RF001 - 5.5 Controle de estoque por movimentacoes (historico operacional). */
@Entity
@Table(name = "movimentacao_estoque")
@Data
public class MovimentacaoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "produto_id", nullable = false)
    private Long produtoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimentacaoEstoque tipo;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(name = "pedido_id")
    private Long pedidoId;

    private String observacao;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm = LocalDateTime.now();
}
