package com.siscomercial.ecommerce.marketplace;

import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.*;
import com.siscomercial.ecommerce.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketplaceOrchestratorServiceTest {
    @Mock IntegracaoMarketplaceRepository integracaoRepository;
    @Mock PublicacaoMarketplaceRepository publicacaoRepository;
    @Mock HistoricoIntegracaoMarketplaceRepository historicoRepository;
    @Mock ProdutoRepository produtoRepository;

    @Test
    void devePublicarPorGatewayEPersistirResultado() {
        MarketplaceGateway gateway = new MarketplaceGateway() {
            public Marketplace marketplace() { return Marketplace.MERCADO_LIVRE; }
            public ResultadoPublicacao publicar(IntegracaoMarketplace i, Produto p) { return new ResultadoPublicacao("MLB1", "https://ml.test/MLB1", "active"); }
        };
        MarketplaceOrchestratorService service = new MarketplaceOrchestratorService(integracaoRepository, publicacaoRepository,
                historicoRepository, produtoRepository, List.of(gateway));
        IntegracaoMarketplace integracao = new IntegracaoMarketplace(); integracao.setId(2L); integracao.setMarketplace(Marketplace.MERCADO_LIVRE); integracao.setStatus(StatusIntegracaoMarketplace.ATIVA);
        Produto produto = new Produto(); produto.setId(3L); produto.setQuantidadeEstoque(5); produto.setQuantidadeReservada(0); produto.setPrecoVenda(new BigDecimal("10.00"));
        when(integracaoRepository.findById(2L)).thenReturn(Optional.of(integracao));
        when(produtoRepository.findById(3L)).thenReturn(Optional.of(produto));
        when(publicacaoRepository.findByProdutoIdAndIntegracaoId(3L, 2L)).thenReturn(Optional.empty());
        when(publicacaoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PublicacaoMarketplace resultado = service.publicar(2L, 3L);

        assertEquals("MLB1", resultado.getIdentificadorExterno());
        assertEquals(StatusPublicacaoMarketplace.PUBLICADA, resultado.getStatus());
        verify(historicoRepository).save(any(HistoricoIntegracaoMarketplace.class));
    }

    @Test
    void deveRejeitarPublicacaoEmIntegracaoInativa() {
        MarketplaceOrchestratorService service = new MarketplaceOrchestratorService(integracaoRepository, publicacaoRepository,
                historicoRepository, produtoRepository, List.of());
        IntegracaoMarketplace integracao = new IntegracaoMarketplace(); integracao.setStatus(StatusIntegracaoMarketplace.INATIVA);
        when(integracaoRepository.findById(2L)).thenReturn(Optional.of(integracao));

        assertThrows(RegraNegocioException.class, () -> service.publicar(2L, 3L));
        verifyNoInteractions(produtoRepository, publicacaoRepository, historicoRepository);
    }
}
