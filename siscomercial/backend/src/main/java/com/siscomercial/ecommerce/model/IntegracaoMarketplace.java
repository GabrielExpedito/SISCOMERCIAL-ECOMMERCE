package com.siscomercial.ecommerce.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** Configuracao de uma loja externa. Segredos nunca devem ser serializados. */
@Entity
@Table(name = "integracao_marketplace", uniqueConstraints =
        @UniqueConstraint(name = "uk_integracao_marketplace_canal_loja", columnNames = {"marketplace", "identificador_externo"}))
@Data
public class IntegracaoMarketplace {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loja_proprietaria", nullable = false)
    private String lojaProprietaria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Marketplace marketplace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StatusIntegracaoMarketplace status = StatusIntegracaoMarketplace.CONFIGURADA;

    @Column(name = "identificador_externo", nullable = false)
    private String identificadorExterno;

    @JsonIgnore
    @Column(name = "credenciais_protegidas", columnDefinition = "TEXT")
    private String credenciaisProtegidas;

    @JsonIgnore
    @Column(name = "token_protegido", columnDefinition = "TEXT")
    private String tokenProtegido;

    @Column(name = "token_expira_em")
    private LocalDateTime tokenExpiraEm;

    @JsonIgnore
    @Column(name = "oauth_state")
    private String oauthState;

    @JsonIgnore
    @Column(name = "oauth_state_expira_em")
    private LocalDateTime oauthStateExpiraEm;

    @Column(name = "ultima_sincronizacao")
    private LocalDateTime ultimaSincronizacao;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm = LocalDateTime.now();

    @PreUpdate
    void atualizarData() {
        atualizadoEm = LocalDateTime.now();
    }
}
