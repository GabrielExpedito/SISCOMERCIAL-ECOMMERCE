package com.siscomercial.ecommerce.marketplace;

import com.siscomercial.ecommerce.model.IntegracaoMarketplace;
import com.siscomercial.ecommerce.model.Marketplace;
import com.siscomercial.ecommerce.model.Produto;

/** Contrato que impede o dominio de conhecer APIs de cada marketplace. */
public interface MarketplaceGateway {
    Marketplace marketplace();

    ResultadoPublicacao publicar(IntegracaoMarketplace integracao, Produto produto);

    record ResultadoPublicacao(String identificadorExterno, String urlPublicacao, String status) {}
}
