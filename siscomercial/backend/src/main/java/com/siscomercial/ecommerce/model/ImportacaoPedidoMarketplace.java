package com.siscomercial.ecommerce.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Registro técnico de uma venda importada do marketplace.
 * Não substitui o Pedido do domínio: esta camada garante idempotência do MKT-005
 * até que o pedido externo seja integrado ao domínio unificado no MKT-011.
 */
@Entity
@Table(name = "importacao_pedido_marketplace", uniqueConstraints =
        @UniqueConstraint(name = "uk_importacao_pedido_marketplace", columnNames = {"integracao_id", "identificador_externo"}))
@Data
public class ImportacaoPedidoMarketplace {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "integracao_id", nullable = false)
    private IntegracaoMarketplace integracao;

    @Column(name = "identificador_externo", nullable = false, length = 80)
    private String identificadorExterno;

    @Column(name = "status_externo", nullable = false, length = 40)
    private String statusExterno;

    @Column(name = "data_criacao_externa")
    private LocalDateTime dataCriacaoExterna;

    @Column(name = "data_atualizacao_externa")
    private LocalDateTime dataAtualizacaoExterna;

    @Column(precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "moeda", length = 10)
    private String moeda;

    @Column(name = "estoque_baixado", nullable = false)
    private boolean estoqueBaixado;

    @Column(name = "importado_em", nullable = false)
    private LocalDateTime importadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm = LocalDateTime.now();

    @Column(name = "ultimo_erro", columnDefinition = "TEXT")
    private String ultimoErro;

    @OneToMany(mappedBy = "importacao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ImportacaoItemPedidoMarketplace> itens = new ArrayList<>();

    @PreUpdate
    void atualizarData() {
        atualizadoEm = LocalDateTime.now();
    }
}
