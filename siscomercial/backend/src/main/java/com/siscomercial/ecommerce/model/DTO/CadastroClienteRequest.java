package com.siscomercial.ecommerce.model.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Entrada pública do cadastro. Campos não listados são ignorados pelo Jackson. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CadastroClienteRequest(
        DadosCadastroCliente cliente,
        String senha
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DadosCadastroCliente(
            String nomeRazaoSocial,
            String cpfCnpj,
            String email,
            String telefone,
            String nomeFantasia,
            String inscricaoEstadual
    ) {}
}
