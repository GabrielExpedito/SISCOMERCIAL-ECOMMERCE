package com.siscomercial.ecommerce.controller;

import com.siscomercial.ecommerce.model.DTO.UsuarioAtualResponse;
import com.siscomercial.ecommerce.model.Cliente;
import com.siscomercial.ecommerce.service.ClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.web.csrf.CsrfToken;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final ClienteService clienteService;

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of("token", csrfToken.getToken());
    }

    @GetMapping("/me")
    public UsuarioAtualResponse usuarioAtual(
            @AuthenticationPrincipal OidcUser usuario) {

        Cliente cliente = clienteService.buscarAutenticado(usuario);

        return new UsuarioAtualResponse(
                cliente.getId(),
                cliente.getNomeRazaoSocial(),
                cliente.getEmail(),
                cliente.getPerfil().name(),
                cliente.getStatus()
        );
    }
}
