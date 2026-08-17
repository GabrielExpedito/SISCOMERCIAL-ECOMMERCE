package com.siscomercial.ecommerce.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** RN007 - Alteracoes de preco devem ser registradas para auditoria. */
@Entity
@Table(name = "historico_preco")
@Data
public class HistoricoPreco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "produto_id", nullable = false)
    private Long produtoId;

    @Column(name = "preco_anterior", precision = 12, scale = 2)
    private BigDecimal precoAnterior;

    @Column(name = "preco_novo", precision = 12, scale = 2)
    private BigDecimal precoNovo;

    @Column(name = "alterado_em")
    private LocalDateTime alteradoEm = LocalDateTime.now();

    @Column(name = "alterado_por")
    private String alteradoPor;
}
