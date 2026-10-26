package com.siscomercial.ecommerce.controller;

import com.siscomercial.ecommerce.model.Cliente;
import com.siscomercial.ecommerce.model.Endereco;
import com.siscomercial.ecommerce.model.DTO.CadastroClienteRequest;
import com.siscomercial.ecommerce.service.ClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/** RF003 - 7.2/7.3/7.4 Cadastro do cliente e enderecos. */
@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @PostMapping
    public Cliente cadastrar(@RequestBody CadastroClienteRequest request) {
        return clienteService.cadastrar(request);
    }

    @GetMapping("/{id}")
    public Cliente buscar(@PathVariable Long id, @AuthenticationPrincipal OidcUser usuario) {
        return clienteService.buscarAutorizado(id, usuario);
    }

    @PostMapping("/{id}/enderecos")
    public Endereco adicionarEndereco(@PathVariable Long id, @RequestBody Endereco endereco,
                                      @AuthenticationPrincipal OidcUser usuario) {
        return clienteService.adicionarEndereco(id, endereco, usuario);
    }
}
