package com.siscomercial.ecommerce.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** Uma publicacao independente do produto em uma integracao. */
@Entity
@Table(name = "publicacao_marketplace", uniqueConstraints =
        @UniqueConstraint(name = "uk_publicacao_produto_integracao", columnNames = {"produto_id", "integracao_id"}))
@Data
public class PublicacaoMarketplace {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "integracao_id", nullable = false)
    private IntegracaoMarketplace integracao;

    @Column(name = "identificador_externo")
    private String identificadorExterno;
    @Column(name = "url_publicacao", columnDefinition = "TEXT")
    private String urlPublicacao;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private StatusPublicacaoMarketplace status = StatusPublicacaoMarketplace.PENDENTE;
    @Column(name = "quantidade_publicada", nullable = false)
    private Integer quantidadePublicada = 0;
    @Column(name = "ultima_sincronizacao")
    private LocalDateTime ultimaSincronizacao;
    @Column(name = "ultimo_erro", columnDefinition = "TEXT")
    private String ultimoErro;
}
