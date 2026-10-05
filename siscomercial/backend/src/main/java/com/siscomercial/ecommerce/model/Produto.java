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

    /** Categoria comercial interna do produto. */
    private String categoria;

    @Column(name = "categoria_mercado_livre_id")
    private String categoriaMercadoLivreId;

    @Column(name = "categoria_mercado_livre_nome")
    private String categoriaMercadoLivreNome;

    /** Categoria e peso utilizados na publicacao via Shopee Open Platform. */
    @Column(name = "categoria_shopee_id")
    private String categoriaShopeeId;

    @Column(name = "peso_shopee_kg", precision = 10, scale = 3)
    private BigDecimal pesoShopeeKg;

    /**
     * Atributos dinamicos informados para a publicacao no Mercado Livre.
     * A estrutura e generica porque os atributos variam conforme a categoria.
     */
    @ElementCollection
    @CollectionTable(
            name = "produto_mercado_livre_atributo",
            joinColumns = @JoinColumn(name = "produto_id")
    )
    private List<AtributoMercadoLivre> atributosMercadoLivre = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusProduto status = StatusProduto.ATIVO;

    @Column(name = "id_externo_mercado_livre")
    private String idExternoMercadoLivre;

    /** ASIN de um item já existente no catálogo Amazon, necessário para listing de oferta. */
    @Column(name = "asin_amazon", length = 10)
    private String asinAmazon;

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
