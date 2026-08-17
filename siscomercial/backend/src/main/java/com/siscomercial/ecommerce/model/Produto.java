package com.siscomercial.ecommerce.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * RF001 - Cadastro e Gestao de Produtos.
 * RN001: codigo interno unico. RN002: preco > 0. RN003: estoque >= 0.
 * RN008: produtos que ja participaram de pedidos nao sao excluidos, apenas inativados.
 */
@Entity
@Table(name = "produto")
@Data
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_interno", nullable = false, unique = true)
    private String codigoInterno;

    @Column(nullable = false)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "preco_venda", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoVenda;

    @Column(name = "preco_promocional", precision = 12, scale = 2)
    private BigDecimal precoPromocional;

    @Column(name = "quantidade_estoque", nullable = false)
    private Integer quantidadeEstoque = 0;

    @Column(name = "quantidade_reservada", nullable = false)
    private Integer quantidadeReservada = 0;

    private String categoria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusProduto status = StatusProduto.ATIVO;

    @Column(name = "id_externo_mercado_livre")
    private String idExternoMercadoLivre;

    @Column(name = "id_externo_facebook")
    private String idExternoFacebook;

    @ElementCollection
    @CollectionTable(name = "produto_imagem", joinColumns = @JoinColumn(name = "produto_id"))
    @Column(name = "url")
    private List<String> imagens = new ArrayList<>();

    @Column(name = "imagem_principal")
    private String imagemPrincipal;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm = LocalDateTime.now();

    /** Estoque efetivamente disponivel para venda (RN012/RN013). */
    @Transient
    public Integer getQuantidadeDisponivel() {
        int reservada = quantidadeReservada == null ? 0 : quantidadeReservada;
        int total = quantidadeEstoque == null ? 0 : quantidadeEstoque;
        return Math.max(0, total - reservada);
    }

    @Transient
    public boolean isEmPromocao() {
        return precoPromocional != null && precoVenda != null
                && precoPromocional.compareTo(precoVenda) < 0;
    }
}
