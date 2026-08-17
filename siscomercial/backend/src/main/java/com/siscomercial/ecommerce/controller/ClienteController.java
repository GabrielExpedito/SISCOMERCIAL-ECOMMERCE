package com.siscomercial.ecommerce.controller;

import com.siscomercial.ecommerce.model.Cliente;
import com.siscomercial.ecommerce.model.Endereco;
import com.siscomercial.ecommerce.service.ClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** RF003 - 7.2/7.3/7.4 Cadastro do cliente e enderecos. */
@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    public record CadastroClienteRequest(Cliente cliente, String senha) {}

    @PostMapping
    public Cliente cadastrar(@RequestBody CadastroClienteRequest request) {
        return clienteService.cadastrar(request.cliente(), request.senha());
    }

    @GetMapping("/{id}")
    public Cliente buscar(@PathVariable Long id) {
        return clienteService.buscarPorId(id);
    }

    @PostMapping("/{id}/enderecos")
    public Endereco adicionarEndereco(@PathVariable Long id, @RequestBody Endereco endereco) {
        return clienteService.adicionarEndereco(id, endereco);
    }
}
