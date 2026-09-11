package com.siscomercial.ecommerce.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Atributo dinamico utilizado na publicacao do produto no Mercado Livre.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AtributoMercadoLivre {

    private String atributoId;

    private String nome;

    private String valueId;

    private String valueName;
}
