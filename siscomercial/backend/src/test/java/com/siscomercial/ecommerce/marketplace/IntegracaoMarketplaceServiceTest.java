package com.siscomercial.ecommerce.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siscomercial.ecommerce.config.AmazonProperties;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.IntegracaoMarketplace;
import com.siscomercial.ecommerce.model.Marketplace;
import com.siscomercial.ecommerce.model.StatusIntegracaoMarketplace;
import com.siscomercial.ecommerce.repository.IntegracaoMarketplaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IntegracaoMarketplaceServiceTest {

    private IntegracaoMarketplaceRepository repository;
    private AmazonProperties amazonProperties;
    private AmazonAuthenticationService amazonAuthenticationService;
    private CredencialMarketplaceService credencialService;
    private ObjectMapper objectMapper;
    private IntegracaoMarketplaceService service;

    @BeforeEach
    void setUp() {
        repository = mock(IntegracaoMarketplaceRepository.class);
        amazonProperties = new AmazonProperties();
        amazonProperties.setClientId("test-client-id");
        amazonProperties.setClientSecret("test-client-secret");
        amazonProperties.setRefreshToken("test-refresh-token");
        amazonProperties.setSellerId("A11V3IV1QHUVIA");
        amazonProperties.setMarketplaceId("A2Q3Y263D00KWC");

        credencialService = mock(CredencialMarketplaceService.class);
        amazonAuthenticationService = mock(AmazonAuthenticationService.class);
        when(amazonAuthenticationService.obterAccessToken()).thenReturn("access-token-test");
        objectMapper = new ObjectMapper();

        service = new IntegracaoMarketplaceService(
                repository,
                amazonProperties,
                amazonAuthenticationService,
                credencialService,
                objectMapper
        );
    }

    @Test
    void criarAmazon_quandoNaoExiste_criaNovaComStatusAtiva() {
        when(repository.findByMarketplaceAndIdentificadorExterno(Marketplace.AMAZON, "A11V3IV1QHUVIA"))
                .thenReturn(Optional.empty());
        when(repository.save(any(IntegracaoMarketplace.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IntegracaoMarketplace resultado = service.criarAmazon("Minha Loja Amazon");

        assertNotNull(resultado);
        assertEquals("Minha Loja Amazon", resultado.getLojaProprietaria());
        assertEquals("A11V3IV1QHUVIA", resultado.getIdentificadorExterno());
        assertEquals(Marketplace.AMAZON, resultado.getMarketplace());
        assertEquals(StatusIntegracaoMarketplace.ATIVA, resultado.getStatus());
        verify(repository).save(resultado);
    }

    @Test
    void criarAmazon_quandoJaExisteInativa_reaproveitaRegistroEAtualizaParaAtivaSemDuplicar() {
        IntegracaoMarketplace existente = new IntegracaoMarketplace();
        existente.setId(3L);
        existente.setMarketplace(Marketplace.AMAZON);
        existente.setIdentificadorExterno("A11V3IV1QHUVIA");
        existente.setLojaProprietaria("Loja Antiga");
        existente.setStatus(StatusIntegracaoMarketplace.INATIVA);

        when(repository.findByMarketplaceAndIdentificadorExterno(Marketplace.AMAZON, "A11V3IV1QHUVIA"))
                .thenReturn(Optional.of(existente));
        when(repository.save(any(IntegracaoMarketplace.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IntegracaoMarketplace resultado = service.criarAmazon("Loja Atualizada");

        assertSame(existente, resultado);
        assertEquals(3L, resultado.getId());
        assertEquals("Loja Atualizada", resultado.getLojaProprietaria());
        assertEquals("A11V3IV1QHUVIA", resultado.getIdentificadorExterno());
        assertEquals(StatusIntegracaoMarketplace.ATIVA, resultado.getStatus());
        verify(repository).save(existente);
    }

    @Test
    void criarAmazon_semCredenciaisLwa_lancaExcecao() {
        amazonProperties.setClientId("");

        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> service.criarAmazon("Minha Loja Amazon"));

        assertTrue(ex.getMessage().contains("Configure as credenciais LWA"));
        verify(repository, never()).save(any());
    }

    @Test
    void criarAmazon_semLojaProprietaria_lancaExcecao() {
        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> service.criarAmazon("   "));

        assertTrue(ex.getMessage().contains("Informe o nome da loja Amazon"));
        verify(repository, never()).save(any());
    }

    @Test
    void alterarAtivacao_amazon_desativaComSucesso() {
        IntegracaoMarketplace integracao = new IntegracaoMarketplace();
        integracao.setId(3L);
        integracao.setMarketplace(Marketplace.AMAZON);
        integracao.setIdentificadorExterno("A11V3IV1QHUVIA");
        integracao.setStatus(StatusIntegracaoMarketplace.ATIVA);

        when(repository.findById(3L)).thenReturn(Optional.of(integracao));
        when(repository.save(any(IntegracaoMarketplace.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IntegracaoMarketplace resultado = service.alterarAtivacao(3L, false);

        assertEquals(StatusIntegracaoMarketplace.INATIVA, resultado.getStatus());
        verify(repository).save(integracao);
    }

    @Test
    void alterarAtivacao_amazon_reativaComSucessoSemExigirTokenOAuth() {
        IntegracaoMarketplace integracao = new IntegracaoMarketplace();
        integracao.setId(3L);
        integracao.setMarketplace(Marketplace.AMAZON);
        integracao.setIdentificadorExterno("A11V3IV1QHUVIA");
        integracao.setStatus(StatusIntegracaoMarketplace.INATIVA);
        integracao.setTokenProtegido(null); // Amazon não possui token OAuth no banco

        when(repository.findById(3L)).thenReturn(Optional.of(integracao));
        when(repository.save(any(IntegracaoMarketplace.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IntegracaoMarketplace resultado = service.alterarAtivacao(3L, true);

        assertEquals(StatusIntegracaoMarketplace.ATIVA, resultado.getStatus());
        verify(repository).save(integracao);
    }

    @Test
    void alterarAtivacao_amazon_semCredenciaisServidor_lancaExcecao() {
        IntegracaoMarketplace integracao = new IntegracaoMarketplace();
        integracao.setId(3L);
        integracao.setMarketplace(Marketplace.AMAZON);
        integracao.setIdentificadorExterno("A11V3IV1QHUVIA");
        integracao.setStatus(StatusIntegracaoMarketplace.INATIVA);

        amazonProperties.setRefreshToken(""); // Credenciais incompletas

        when(repository.findById(3L)).thenReturn(Optional.of(integracao));

        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> service.alterarAtivacao(3L, true));

        assertTrue(ex.getMessage().contains("Configure as credenciais LWA"));
        verify(repository, never()).save(any());
    }

    @Test
    void alterarAtivacao_amazon_comFalhaLwa_preservaStatusPersistido() {
        IntegracaoMarketplace integracao = new IntegracaoMarketplace();
        integracao.setId(3L);
        integracao.setMarketplace(Marketplace.AMAZON);
        integracao.setStatus(StatusIntegracaoMarketplace.INATIVA);
        when(repository.findById(3L)).thenReturn(Optional.of(integracao));
        when(amazonAuthenticationService.obterAccessToken())
                .thenThrow(new RegraNegocioException("LWA_AUTH (HTTP 401)"));

        RegraNegocioException error = assertThrows(RegraNegocioException.class,
                () -> service.alterarAtivacao(3L, true));

        assertTrue(error.getMessage().contains("LWA_AUTH"));
        assertEquals(StatusIntegracaoMarketplace.INATIVA, integracao.getStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void alterarAtivacao_mercadoLivre_semTokenProtegido_lancaExcecaoOAuth() {
        IntegracaoMarketplace integracao = new IntegracaoMarketplace();
        integracao.setId(1L);
        integracao.setMarketplace(Marketplace.MERCADO_LIVRE);
        integracao.setStatus(StatusIntegracaoMarketplace.CONFIGURADA);
        integracao.setTokenProtegido(null);

        when(repository.findById(1L)).thenReturn(Optional.of(integracao));

        RegraNegocioException ex = assertThrows(RegraNegocioException.class,
                () -> service.alterarAtivacao(1L, true));

        assertTrue(ex.getMessage().contains("Conclua a autorizacao OAuth antes de ativar"));
        verify(repository, never()).save(any());
    }

    @Test
    void alterarAtivacao_mercadoLivre_comTokenProtegido_ativaComSucesso() {
        IntegracaoMarketplace integracao = new IntegracaoMarketplace();
        integracao.setId(1L);
        integracao.setMarketplace(Marketplace.MERCADO_LIVRE);
        integracao.setStatus(StatusIntegracaoMarketplace.INATIVA);
        integracao.setTokenProtegido("token-encriptado");

        when(repository.findById(1L)).thenReturn(Optional.of(integracao));
        when(repository.save(any(IntegracaoMarketplace.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IntegracaoMarketplace resultado = service.alterarAtivacao(1L, true);

        assertEquals(StatusIntegracaoMarketplace.ATIVA, resultado.getStatus());
        verify(repository).save(integracao);
    }

    @Test
    void diagnosticar_amazon_obtemTokenLwaSemDeclararSpApiValidadaOuAlterarStatus() {
        IntegracaoMarketplace integracao = new IntegracaoMarketplace();
        integracao.setId(3L);
        integracao.setMarketplace(Marketplace.AMAZON);
        integracao.setStatus(StatusIntegracaoMarketplace.ERRO);

        when(repository.findById(3L)).thenReturn(Optional.of(integracao));
        when(repository.save(any(IntegracaoMarketplace.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IntegracaoMarketplace resultado = service.diagnosticar(3L);

        assertSame(integracao, resultado);
        assertEquals(StatusIntegracaoMarketplace.ERRO, resultado.getStatus());
        assertNull(resultado.getUltimaSincronizacao());
        verify(amazonAuthenticationService).obterAccessToken();
        verify(repository, never()).save(any());
    }

    @Test
    void diagnosticar_amazon_preservaFalhaLwaEEstadoPersistido() {
        IntegracaoMarketplace integracao = new IntegracaoMarketplace();
        integracao.setId(3L);
        integracao.setMarketplace(Marketplace.AMAZON);
        integracao.setStatus(StatusIntegracaoMarketplace.ATIVA);
        when(repository.findById(3L)).thenReturn(Optional.of(integracao));
        when(amazonAuthenticationService.obterAccessToken())
                .thenThrow(new RegraNegocioException("LWA LWA_AUTH (HTTP 401)"));

        RegraNegocioException error = assertThrows(RegraNegocioException.class, () -> service.diagnosticar(3L));

        assertTrue(error.getMessage().contains("LWA_AUTH"));
        verify(repository, never()).save(any());
    }

    @Test
    void criarMercadoLivre_quandoJaExiste_reaproveitaRegistroExistente() {
        IntegracaoMarketplace existente = new IntegracaoMarketplace();
        existente.setId(10L);
        existente.setMarketplace(Marketplace.MERCADO_LIVRE);
        existente.setIdentificadorExterno("LOJA_ML_1");
        existente.setLojaProprietaria("Loja Velha");

        when(repository.findByMarketplaceAndIdentificadorExterno(Marketplace.MERCADO_LIVRE, "LOJA_ML_1"))
                .thenReturn(Optional.of(existente));
        when(repository.save(any(IntegracaoMarketplace.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IntegracaoMarketplace resultado = service.criarMercadoLivre("Loja Nova", "LOJA_ML_1");

        assertSame(existente, resultado);
        assertEquals(10L, resultado.getId());
        assertEquals("Loja Nova", resultado.getLojaProprietaria());
        assertEquals(StatusIntegracaoMarketplace.CONFIGURADA, resultado.getStatus());
        verify(repository).save(existente);
    }

    @Test
    void criarShopee_quandoJaExiste_reaproveitaRegistroExistente() {
        ReflectionTestUtils.setField(service, "shopeePartnerId", "123");
        ReflectionTestUtils.setField(service, "shopeePartnerKey", "key");
        ReflectionTestUtils.setField(service, "shopeeRedirectUri", "https://redirect.test");

        IntegracaoMarketplace existente = new IntegracaoMarketplace();
        existente.setId(20L);
        existente.setMarketplace(Marketplace.SHOPEE);
        existente.setIdentificadorExterno("SHOP_99");
        existente.setLojaProprietaria("Shopee Antiga");

        when(repository.findByMarketplaceAndIdentificadorExterno(Marketplace.SHOPEE, "SHOP_99"))
                .thenReturn(Optional.of(existente));
        when(repository.save(any(IntegracaoMarketplace.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IntegracaoMarketplace resultado = service.criarShopee("Shopee Nova", "SHOP_99");

        assertSame(existente, resultado);
        assertEquals(20L, resultado.getId());
        assertEquals("Shopee Nova", resultado.getLojaProprietaria());
        assertEquals(StatusIntegracaoMarketplace.CONFIGURADA, resultado.getStatus());
        verify(repository).save(existente);
    }
}

