package com.siscomercial.ecommerce.marketplace;

import com.siscomercial.ecommerce.model.IntegracaoMarketplace;
import com.siscomercial.ecommerce.model.Marketplace;
import com.siscomercial.ecommerce.model.Produto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Contrato que impede o dominio de conhecer APIs de cada marketplace.
 */
public interface MarketplaceGateway {

    Marketplace marketplace();

    ResultadoPublicacao publicar(
            IntegracaoMarketplace integracao,
            Produto produto
    );

    ResultadoOperacao encerrar(
            IntegracaoMarketplace integracao,
            String identificadorExterno
    );

    ResultadoSincronizacao sincronizar(
            IntegracaoMarketplace integracao,
            String identificadorExterno
    );

    /**
     * Busca vendas do marketplace alteradas a partir da data informada.
     *
     * Gateways que ainda não possuem integração de pedidos
     * mantêm o comportamento neutro.
     */
    default List<ResultadoPedido> buscarPedidos(
            IntegracaoMarketplace integracao,
            LocalDateTime alteradosDesde
    ) {
        return List.of();
    }

    record ResultadoPublicacao(
            String identificadorExterno,
            String urlPublicacao,
            String status
    ) {}

    record ResultadoOperacao(
            String status
    ) {}

    record ResultadoSincronizacao(
            String status,
            Integer quantidadeDisponivel,
            String permalink
    ) {}

    record ResultadoPedido(
            String identificadorExterno,
            String status,
            LocalDateTime dataCriacao,
            LocalDateTime dataAtualizacao,
            BigDecimal total,
            String moeda,
            List<ResultadoItemPedido> itens
    ) {}

    record ResultadoItemPedido(
            String identificadorItemExterno,
            int quantidade,
            BigDecimal valorUnitario
    ) {}
}