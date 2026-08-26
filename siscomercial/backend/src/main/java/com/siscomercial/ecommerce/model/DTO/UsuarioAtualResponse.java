package com.siscomercial.ecommerce.model.DTO;

import com.siscomercial.ecommerce.model.PerfilUsuario;

public record UsuarioAtualResponse(
        Long id,
        String nomeRazaoSocial,
        String email,
        String perfil,
        String status
) {
}