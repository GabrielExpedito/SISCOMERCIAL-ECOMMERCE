package com.siscomercial.ecommerce.controller;

import com.siscomercial.ecommerce.service.ImagemProdutoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/retaguarda/produtos/{produtoId}/imagens")
@RequiredArgsConstructor
public class ProdutoImagemController {

    private final ImagemProdutoService imagemProdutoService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImagemProdutoService.ImagemResponse adicionar(
            @PathVariable Long produtoId,
            @RequestParam("file") MultipartFile file) {
        return imagemProdutoService.adicionar(produtoId, file);
    }

    @PutMapping("/principal")
    public ImagemProdutoService.ImagemResponse principal(
            @PathVariable Long produtoId,
            @RequestBody Map<String, String> body) {
        return imagemProdutoService.principal(produtoId, body.get("url"));
    }

    @DeleteMapping
    public ResponseEntity<Void> remover(
            @PathVariable Long produtoId,
            @RequestParam String url) {
        imagemProdutoService.remover(produtoId, url);
        return ResponseEntity.noContent().build();
    }
}
