package com.siscomercial.ecommerce.ai;

import java.math.BigDecimal;

public record DadosCadastroProduto(
        String codigoInterno,
        String nome,
        String descricao,
        BigDecimal precoVenda,
        Integer quantidadeEstoque,
        String categoria,
        BigDecimal precoPromocional,
        String imagemPrincipal
) {
}