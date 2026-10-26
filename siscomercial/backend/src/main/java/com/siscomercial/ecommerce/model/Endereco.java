package com.siscomercial.ecommerce.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

/** RF003 - 7.4 Endereco de entrega. */
@Entity
@Table(name = "endereco")
@Data
public class Endereco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cliente_id")
    @JsonIgnore
    @ToString.Exclude
    private Cliente cliente;

    @ToString.Exclude
    @Column(nullable = false)
    private String cep;

    @ToString.Exclude
    private String logradouro;
    @ToString.Exclude
    private String numero;
    @ToString.Exclude
    private String complemento;
    @ToString.Exclude
    private String bairro;
    @ToString.Exclude
    private String cidade;
    @ToString.Exclude
    private String estado;

    @Column(name = "principal")
    private boolean principal = false;
}
