package com.siscomercial.ecommerce.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.exception.RecursoNaoEncontradoException;
import com.siscomercial.ecommerce.config.AmazonProperties;
import com.siscomercial.ecommerce.model.*;
import com.siscomercial.ecommerce.repository.IntegracaoMarketplaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;

/** Configura, ativa e diagnostica contas sem retornar segredos ao cliente. */
@Service
@RequiredArgsConstructor
public class IntegracaoMarketplaceService {
    private final IntegracaoMarketplaceRepository repository;
    private final AmazonProperties amazonProperties;
    private final CredencialMarketplaceService credencialService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    @Value("${siscomercial.mercado-livre.client-id:}") private String clientId;
    @Value("${siscomercial.mercado-livre.client-secret:}") private String clientSecret;
    @Value("${siscomercial.mercado-livre.redirect-uri:}") private String redirectUri;
    @Value("${siscomercial.shopee.partner-id:}") private String shopeePartnerId;
    @Value("${siscomercial.shopee.partner-key:}") private String shopeePartnerKey;
    @Value("${siscomercial.shopee.redirect-uri:}") private String shopeeRedirectUri;

    @Transactional
    public IntegracaoMarketplace criarMercadoLivre(String lojaProprietaria, String identificadorExterno) {
        if (lojaProprietaria == null || lojaProprietaria.isBlank() || identificadorExterno == null || identificadorExterno.isBlank()) {
            throw new RegraNegocioException("Loja proprietaria e identificador externo sao obrigatorios.");
        }
        IntegracaoMarketplace integracao = new IntegracaoMarketplace();
        integracao.setLojaProprietaria(lojaProprietaria.trim());
        integracao.setIdentificadorExterno(identificadorExterno.trim());
        integracao.setMarketplace(Marketplace.MERCADO_LIVRE);
        integracao.setStatus(StatusIntegracaoMarketplace.CONFIGURADA);
        return repository.save(integracao);
    }

    @Transactional
    public String iniciarAutorizacaoMercadoLivre(Long integracaoId) {
        exigirConfiguracaoOAuth();
        IntegracaoMarketplace integracao = buscarMercadoLivre(integracaoId);
        String state = UUID.randomUUID().toString();
        integracao.setOauthState(state);
        integracao.setOauthStateExpiraEm(LocalDateTime.now().plusMinutes(10));
        repository.save(integracao);
        return "https://auth.mercadolivre.com.br/authorization?response_type=code&client_id=" + codificar(clientId)
                + "&redirect_uri=" + codificar(redirectUri) + "&state=" + codificar(state);
    }

    @Transactional
    public IntegracaoMarketplace concluirAutorizacaoMercadoLivre(String code, String state) {
        exigirConfiguracaoOAuth();
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new RegraNegocioException("Retorno OAuth do Mercado Livre invalido ou expirado.");
        }
        IntegracaoMarketplace integracao = repository.findByOauthState(state)
                .orElseThrow(() -> new RegraNegocioException("Retorno OAuth do Mercado Livre invalido ou expirado."));
        if (!state.equals(integracao.getOauthState())
                || integracao.getOauthStateExpiraEm() == null || integracao.getOauthStateExpiraEm().isBefore(LocalDateTime.now())) {
            throw new RegraNegocioException("Retorno OAuth do Mercado Livre invalido ou expirado.");
        }
        try {
            String body = "grant_type=authorization_code&client_id=" + codificar(clientId)
                    + "&client_secret=" + codificar(clientSecret) + "&code=" + codificar(code)
                    + "&redirect_uri=" + codificar(redirectUri);
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mercadolibre.com/oauth/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode token = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || token.path("access_token").asText().isBlank()) {
                throw new RegraNegocioException("Mercado Livre recusou a autorizacao: " + token.path("message").asText("erro sem detalhes"));
            }
            String accessToken = token.path("access_token").asText();
            JsonNode usuario = consultarUsuario(accessToken);
            integracao.setTokenProtegido(credencialService.proteger(accessToken));
            integracao.setCredenciaisProtegidas(credencialService.proteger(token.path("refresh_token").asText()));
            integracao.setIdentificadorExterno(usuario.path("id").asText(integracao.getIdentificadorExterno()));
            integracao.setLojaProprietaria(usuario.path("nickname").asText(integracao.getLojaProprietaria()));
            integracao.setTokenExpiraEm(LocalDateTime.now().plusSeconds(token.path("expires_in").asLong(0)));
            integracao.setUltimaSincronizacao(LocalDateTime.now());
            integracao.setOauthState(null); integracao.setOauthStateExpiraEm(null);
            integracao.setStatus(StatusIntegracaoMarketplace.ATIVA);
            return repository.save(integracao);
        } catch (RegraNegocioException e) { throw e;
        } catch (Exception e) { throw new RegraNegocioException("Falha ao concluir autorizacao Mercado Livre: " + e.getMessage()); }
    }

    @Transactional
    public IntegracaoMarketplace criarShopee(String lojaProprietaria, String shopId) {
        exigirConfiguracaoShopee();
        if (lojaProprietaria == null || lojaProprietaria.isBlank() || shopId == null || shopId.isBlank()) {
            throw new RegraNegocioException("Loja proprietaria e shop_id sao obrigatorios.");
        }
        IntegracaoMarketplace integracao = new IntegracaoMarketplace();
        integracao.setLojaProprietaria(lojaProprietaria.trim());
        integracao.setIdentificadorExterno(shopId.trim());
        integracao.setMarketplace(Marketplace.SHOPEE);
        integracao.setStatus(StatusIntegracaoMarketplace.CONFIGURADA);
        return repository.save(integracao);
    }

    @Transactional
    public IntegracaoMarketplace criarAmazon(String lojaProprietaria) {
        if (!amazonProperties.hasLwaCredentials() || amazonProperties.getSellerId().isBlank()
                || amazonProperties.getMarketplaceId().isBlank()) {
            throw new RegraNegocioException("Configure as credenciais LWA, AMAZON_SELLER_ID e AMAZON_MARKETPLACE_ID no backend.");
        }
        if (lojaProprietaria == null || lojaProprietaria.isBlank()) {
            throw new RegraNegocioException("Informe o nome da loja Amazon.");
        }
        IntegracaoMarketplace integracao = new IntegracaoMarketplace();
        integracao.setLojaProprietaria(lojaProprietaria.trim());
        integracao.setIdentificadorExterno(amazonProperties.getSellerId());
        integracao.setMarketplace(Marketplace.AMAZON);
        integracao.setStatus(StatusIntegracaoMarketplace.ATIVA);
        return repository.save(integracao);
    }

    @Transactional
    public String iniciarAutorizacaoShopee(Long integracaoId) {
        exigirConfiguracaoShopee();
        IntegracaoMarketplace integracao = buscarShopee(integracaoId);
        String state = UUID.randomUUID().toString();
        integracao.setOauthState(state);
        integracao.setOauthStateExpiraEm(LocalDateTime.now().plusMinutes(10));
        repository.save(integracao);
        long timestamp = java.time.Instant.now().getEpochSecond();
        String path = "/api/v2/shop/auth_partner";
        String sign = assinar(path, timestamp, "", "");
        return "https://partner.shopeemobile.com" + path + "?partner_id=" + codificar(shopeePartnerId)
                + "&timestamp=" + timestamp + "&sign=" + sign + "&redirect=" + codificar(shopeeRedirectUri)
                + "&state=" + codificar(state);
    }

    @Transactional
    public IntegracaoMarketplace concluirAutorizacaoShopee(String code, String shopId, String state) {
        exigirConfiguracaoShopee();
        if (code == null || code.isBlank() || shopId == null || shopId.isBlank() || state == null || state.isBlank()) {
            throw new RegraNegocioException("Retorno OAuth da Shopee invalido ou expirado.");
        }
        IntegracaoMarketplace integracao = repository.findByOauthState(state)
                .orElseThrow(() -> new RegraNegocioException("Retorno OAuth da Shopee invalido ou expirado."));
        if (integracao.getMarketplace() != Marketplace.SHOPEE
                || integracao.getOauthStateExpiraEm() == null
                || integracao.getOauthStateExpiraEm().isBefore(LocalDateTime.now())) {
            throw new RegraNegocioException("Retorno OAuth da Shopee invalido ou expirado.");
        }
        try {
            long timestamp = java.time.Instant.now().getEpochSecond();
            String path = "/api/v2/auth/token/get";
            String sign = assinar(path, timestamp, "", "");
            Map<String, Object> body = Map.of("code", code, "shop_id", Long.parseLong(shopId), "partner_id", Long.parseLong(shopeePartnerId));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://partner.shopeemobile.com" + path
                            + "?partner_id=" + codificar(shopeePartnerId) + "&timestamp=" + timestamp + "&sign=" + sign))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode token = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || token.path("access_token").asText().isBlank()) {
                throw new RegraNegocioException("Shopee recusou a autorizacao: " + token.path("message").asText("erro sem detalhes"));
            }
            integracao.setTokenProtegido(credencialService.proteger(token.path("access_token").asText()));
            integracao.setCredenciaisProtegidas(credencialService.proteger(token.path("refresh_token").asText()));
            integracao.setIdentificadorExterno(shopId.trim());
            integracao.setTokenExpiraEm(LocalDateTime.now().plusSeconds(token.path("expire_in").asLong(0)));
            integracao.setOauthState(null);
            integracao.setOauthStateExpiraEm(null);
            integracao.setUltimaSincronizacao(LocalDateTime.now());
            integracao.setStatus(StatusIntegracaoMarketplace.ATIVA);
            return repository.save(integracao);
        } catch (RegraNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new RegraNegocioException("Falha ao concluir autorizacao Shopee: " + e.getMessage());
        }
    }

    @Transactional
    public IntegracaoMarketplace diagnosticarShopee(Long id) {
        IntegracaoMarketplace integracao = buscarShopee(id);
        if (integracao.getTokenProtegido() == null || integracao.getTokenProtegido().isBlank()) {
            throw new RegraNegocioException("A integracao Shopee ainda nao possui credencial valida para diagnostico.");
        }
        try {
            String token = credencialService.revelar(integracao.getTokenProtegido());
            long timestamp = java.time.Instant.now().getEpochSecond();
            String path = "/api/v2/shop/get_shop_info";
            String sign = assinar(path, timestamp, token, integracao.getIdentificadorExterno());
            URI uri = URI.create("https://partner.shopeemobile.com" + path
                    + "?partner_id=" + codificar(shopeePartnerId)
                    + "&timestamp=" + timestamp
                    + "&access_token=" + codificar(token)
                    + "&shop_id=" + codificar(integracao.getIdentificadorExterno())
                    + "&sign=" + sign);
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !json.path("error").asText("").isBlank()) {
                throw new RegraNegocioException("Falha ao diagnosticar a integracao Shopee: "
                        + json.path("message").asText(json.path("error").asText("erro sem detalhes")));
            }
            integracao.setUltimaSincronizacao(LocalDateTime.now());
            if (integracao.getStatus() == StatusIntegracaoMarketplace.ERRO) {
                integracao.setStatus(StatusIntegracaoMarketplace.ATIVA);
            }
            return repository.save(integracao);
        } catch (RegraNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new RegraNegocioException("Falha ao diagnosticar a integracao Shopee: " + e.getMessage());
        }
    }

    @Transactional
    public IntegracaoMarketplace alterarAtivacao(Long id, boolean ativa) {
        IntegracaoMarketplace integracao = buscar(id);
        if (ativa && (integracao.getTokenProtegido() == null || integracao.getTokenProtegido().isBlank())) {
            throw new RegraNegocioException("Conclua a autorizacao OAuth antes de ativar a integracao.");
        }
        integracao.setStatus(ativa ? StatusIntegracaoMarketplace.ATIVA : StatusIntegracaoMarketplace.INATIVA);
        return repository.save(integracao);
    }

    @Transactional
    public IntegracaoMarketplace diagnosticar(Long id) {
        IntegracaoMarketplace integracao = buscar(id);
        if (integracao.getMarketplace() != Marketplace.MERCADO_LIVRE || integracao.getTokenProtegido() == null) {
            throw new RegraNegocioException("A integracao ainda nao possui credencial valida para diagnostico.");
        }
        consultarUsuario(credencialService.revelar(integracao.getTokenProtegido()));
        integracao.setUltimaSincronizacao(LocalDateTime.now());
        if (integracao.getStatus() == StatusIntegracaoMarketplace.ERRO) integracao.setStatus(StatusIntegracaoMarketplace.ATIVA);
        return repository.save(integracao);
    }

    public java.util.List<IntegracaoMarketplace> listar() { return repository.findAll(); }
    public IntegracaoMarketplace buscar(Long id) { return repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Integracao de marketplace nao encontrada: " + id)); }
    private IntegracaoMarketplace buscarMercadoLivre(Long id) { IntegracaoMarketplace i = buscar(id); if (i.getMarketplace() != Marketplace.MERCADO_LIVRE) throw new RegraNegocioException("Esta operacao e exclusiva do Mercado Livre."); return i; }
    private JsonNode consultarUsuario(String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mercadolibre.com/users/me"))
                    .header("Authorization", "Bearer " + token).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new RegraNegocioException("Token do Mercado Livre invalido (HTTP " + response.statusCode() + ").");
            return objectMapper.readTree(response.body());
        } catch (RegraNegocioException e) { throw e;
        } catch (Exception e) { throw new RegraNegocioException("Falha ao consultar conta Mercado Livre: " + e.getMessage()); }
    }
    private IntegracaoMarketplace buscarShopee(Long id) {
        IntegracaoMarketplace i = buscar(id);
        if (i.getMarketplace() != Marketplace.SHOPEE) throw new RegraNegocioException("Esta operacao e exclusiva da Shopee.");
        return i;
    }

    private void exigirConfiguracaoShopee() {
        if (shopeePartnerId.isBlank() || shopeePartnerKey.isBlank() || shopeeRedirectUri.isBlank()) {
            throw new RegraNegocioException("Configure SHOPEE_PARTNER_ID, SHOPEE_PARTNER_KEY e SHOPEE_REDIRECT_URI no backend.");
        }
    }

    private String assinar(String path, long timestamp, String accessToken, String shopId) {
        try {
            String base = shopeePartnerId + path + timestamp + accessToken + shopId;
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(shopeePartnerKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RegraNegocioException("Nao foi possivel gerar a assinatura da Shopee.");
        }
    }

    private void exigirConfiguracaoOAuth() { if (clientId.isBlank() || clientSecret.isBlank() || redirectUri.isBlank()) throw new RegraNegocioException("Configure as variaveis de ambiente do Mercado Livre no backend."); }
    private String codificar(String valor) { return URLEncoder.encode(valor, StandardCharsets.UTF_8); }
}
