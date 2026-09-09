package com.siscomercial.ecommerce.model.DTO;

import com.siscomercial.ecommerce.model.*;
import java.time.LocalDateTime;

public final class MarketplaceDTOs {
    private MarketplaceDTOs() {}
    public record CriarIntegracaoRequest(String lojaProprietaria, String identificadorExterno) {}
    public record AlterarAtivacaoRequest(boolean ativa) {}
    public record PublicarProdutoRequest(Long integracaoId, Long produtoId) {}
    public record IntegracaoResponse(Long id, String lojaProprietaria, Marketplace marketplace,
                                     StatusIntegracaoMarketplace status, String identificadorExterno,
                                     LocalDateTime tokenExpiraEm, LocalDateTime ultimaSincronizacao) {}
    public record UrlAutorizacaoResponse(String urlAutorizacao) {}
    public record PublicacaoResponse(Long id, Long produtoId, Long integracaoId, String identificadorExterno,
                                     String urlPublicacao, StatusPublicacaoMarketplace status, Integer quantidadePublicada,
                                     LocalDateTime ultimaSincronizacao, String ultimoErro) {}
}
