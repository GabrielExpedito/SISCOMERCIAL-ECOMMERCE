package com.siscomercial.ecommerce.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** RF003 - 7.3 Dados do cliente. */
@Entity
@Table(name = "cliente")
@Data
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_razao_social", nullable = false)
    @ToString.Exclude
    private String nomeRazaoSocial;

    @Column(name = "cpf_cnpj", unique = true)
    @ToString.Exclude
    private String cpfCnpj;

    @Column(nullable = false, unique = true)
    @ToString.Exclude
    private String email;

    @ToString.Exclude
    private String telefone;

    @Column(name = "senha_hash")
    @JsonIgnore
    @ToString.Exclude
    private String senhaHash;

    @Column(name = "google_id", unique = true)
    @ToString.Exclude
    private String googleId;

    @Column(name = "nome_fantasia")
    @ToString.Exclude
    private String nomeFantasia;

    @Column(name = "inscricao_estadual")
    @ToString.Exclude
    private String inscricaoEstadual;

    @Column(name = "data_cadastro")
    private LocalDateTime dataCadastro = LocalDateTime.now();

    @Column(nullable = false)
    private String status = "ATIVO";

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<Endereco> enderecos = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PerfilUsuario perfil = PerfilUsuario.CLIENTE;
}
