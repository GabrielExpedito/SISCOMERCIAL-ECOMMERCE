package com.siscomercial.ecommerce.model;

import jakarta.persistence.*;
import lombok.Data;

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
    private Cliente cliente;

    @Column(nullable = false)
    private String cep;

    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;
    private String estado;

    @Column(name = "principal")
    private boolean principal = false;
}
