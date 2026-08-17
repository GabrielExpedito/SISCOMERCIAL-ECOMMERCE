package com.siscomercial.ecommerce.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * RF003 - 7.9/7.10 Criacao e dados do pedido.
 * RN024: apos pagamento aprovado o pedido nao pode ser alterado livremente.
 * RN025: o pedido preserva um "snapshot" dos dados da venda (precos e endereco).
 */
@Entity
@Table(name = "pedido")
@Data
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Numero publico do pedido, ex: SIS-2026-000001 (secao 7.9). */
    @Column(name = "numero_pedido", nullable = false, unique = true)
    private String numeroPedido;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPedido status = StatusPedido.AGUARDANDO_PAGAMENTO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal frete = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    // Snapshot do endereco de entrega no momento da compra (RN025)
    private String enderecoCep;
    private String enderecoLogradouro;
    private String enderecoNumero;
    private String enderecoComplemento;
    private String enderecoBairro;
    private String enderecoCidade;
    private String enderecoEstado;

    @Column(name = "modalidade_frete")
    private String modalidadeFrete;

    @Column(name = "prazo_estimado_dias")
    private Integer prazoEstimadoDias;

    @Column(name = "codigo_rastreamento")
    private String codigoRastreamento;

    @Column(name = "reserva_expira_em")
    private LocalDateTime reservaExpiraEm;

    @Column(name = "numero_nota_fiscal")
    private String numeroNotaFiscal;

    @Column(name = "chave_nota_fiscal")
    private String chaveNotaFiscal;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemPedido> itens = new ArrayList<>();

    @OneToOne(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private Pagamento pagamento;

    /** RN024: define quais status ainda permitem alteracao/cancelamento livre. */
    @Transient
    public boolean isAlteravelLivremente() {
        return status == StatusPedido.AGUARDANDO_PAGAMENTO;
    }
}
