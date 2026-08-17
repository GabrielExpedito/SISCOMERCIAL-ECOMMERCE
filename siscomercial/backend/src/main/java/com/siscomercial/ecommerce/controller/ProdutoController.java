package com.siscomercial.ecommerce.controller;

import com.siscomercial.ecommerce.model.Produto;
import com.siscomercial.ecommerce.service.ProdutoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** RF001 - Retaguarda: cadastro e gestao de produtos. */
@RestController
@RequestMapping("/api/retaguarda/produtos")
@RequiredArgsConstructor
public class ProdutoController {

    private final ProdutoService produtoService;

    @GetMapping
    public List<Produto> listar() {
        return produtoService.listarTodos();
    }

    @GetMapping("/{id}")
    public Produto buscar(@PathVariable Long id) {
        return produtoService.buscarPorId(id);
    }

    @PostMapping
    public Produto criar(@RequestBody Produto produto) {
        return produtoService.criar(produto);
    }

    @PutMapping("/{id}")
    public Produto alterar(@PathVariable Long id, @RequestBody Produto produto, @RequestParam(defaultValue = "admin") String usuario) {
        return produtoService.alterar(id, produto, usuario);
    }

    @PostMapping("/{id}/inativar")
    public void inativar(@PathVariable Long id) {
        produtoService.inativar(id);
    }

    @PostMapping("/{id}/reativar")
    public void reativar(@PathVariable Long id) {
        produtoService.reativar(id);
    }
}
