package com.siscomercial.ecommerce.ai;

public record DadosCadastroProduto(
        String codigoInterno,
        String nome,
        String descricao,
        Double precoVenda,
        Integer quantidadeEstoque,
        String categoria,
        Double precoPromocional,
        String imagemPrincipal
) {
}