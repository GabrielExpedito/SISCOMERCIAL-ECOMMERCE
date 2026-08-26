package com.siscomercial.ecommerce.controller;

import com.siscomercial.ecommerce.model.DTO.UsuarioAtualResponse;
import com.siscomercial.ecommerce.model.Cliente;
import com.siscomercial.ecommerce.service.ClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final ClienteService clienteService;

    @GetMapping("/me")
    public UsuarioAtualResponse usuarioAtual(
            @AuthenticationPrincipal OAuth2User usuario) {

        String email = usuario.getAttribute("email");

        Cliente cliente = clienteService.buscarPorEmail(email);

        return new UsuarioAtualResponse(
                cliente.getId(),
                cliente.getNomeRazaoSocial(),
                cliente.getEmail(),
                cliente.getPerfil().name(),
                cliente.getStatus()
        );
    }
}