package com.siscomercial.ecommerce.controller;

import com.siscomercial.ecommerce.model.Produto;
import com.siscomercial.ecommerce.service.FreteService;
import com.siscomercial.ecommerce.service.ProdutoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** RF002 - Catalogo e Pagina de Produtos (interface publica). */
@RestController
@RequestMapping("/api/catalogo")
@RequiredArgsConstructor
public class CatalogoController {

    private final ProdutoService produtoService;
    private final FreteService freteService;

    @GetMapping("/produtos")
    public List<Produto> catalogo() {
        // RN009 - somente produtos ATIVOS
        return produtoService.listarCatalogoPublico();
    }

    @GetMapping("/produtos/pesquisar")
    public List<Produto> pesquisar(@RequestParam String q) {
        return produtoService.pesquisar(q);
    }

    @GetMapping("/produtos/{codigo}")
    public Produto detalhe(@PathVariable String codigo) {
        return produtoService.buscarPorCodigo(codigo);
    }

    @GetMapping("/frete")
    public FreteService.CotacaoFrete calcularFrete(@RequestParam String cep) {
        return freteService.calcular(cep);
    }
}
