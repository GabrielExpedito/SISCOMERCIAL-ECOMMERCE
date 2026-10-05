package com.siscomercial.ecommerce.controller;

import com.siscomercial.ecommerce.application.MarketplaceApplicationService;
import com.siscomercial.ecommerce.marketplace.IntegracaoMarketplaceService;
import com.siscomercial.ecommerce.marketplace.MercadoLivreCategoriaService;
import com.siscomercial.ecommerce.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MarketplaceControllerTest {
    private MarketplaceApplicationService applicationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = mock(MarketplaceApplicationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MarketplaceController(
                mock(IntegracaoMarketplaceService.class), mock(MercadoLivreCategoriaService.class), applicationService
        )).build();
    }

    @Test
    void endpointMulticanalRetornaResultadosPorMarketplace() throws Exception {
        when(applicationService.publicarProdutoMulticanal(12L)).thenReturn(List.of(publicacao(4L, 12L)));

        mockMvc.perform(post("/api/retaguarda/marketplaces/publicacoes/multicanal")
                        .contentType("application/json").content("{\"produtoId\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].integracaoId").value(4))
                .andExpect(jsonPath("$[0].status").value("PUBLICADA"));
    }

    @Test
    void endpointIndividualExistenteContinuaDisponivel() throws Exception {
        when(applicationService.publicarProduto(4L, 12L)).thenReturn(publicacao(4L, 12L));

        mockMvc.perform(post("/api/retaguarda/marketplaces/publicacoes")
                        .contentType("application/json").content("{\"integracaoId\":4,\"produtoId\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.integracaoId").value(4))
                .andExpect(jsonPath("$.status").value("PUBLICADA"));
    }

    private PublicacaoMarketplace publicacao(Long integracaoId, Long produtoId) {
        IntegracaoMarketplace integracao = new IntegracaoMarketplace();
        integracao.setId(integracaoId);
        integracao.setMarketplace(Marketplace.MERCADO_LIVRE);
        Produto produto = new Produto();
        produto.setId(produtoId);
        produto.setNome("Produto teste");
        produto.setCodigoInterno("SKU-TESTE");
        PublicacaoMarketplace publicacao = new PublicacaoMarketplace();
        publicacao.setId(30L);
        publicacao.setIntegracao(integracao);
        publicacao.setProduto(produto);
        publicacao.setStatus(StatusPublicacaoMarketplace.PUBLICADA);
        publicacao.setIdentificadorExterno("MLB-TESTE");
        return publicacao;
    }
}
