package com.siscomercial.ecommerce.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.exception.RecursoNaoEncontradoException;
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

/** Configura, ativa e diagnostica contas sem retornar segredos ao cliente. */
@Service
@RequiredArgsConstructor
public class IntegracaoMarketplaceService {
    private final IntegracaoMarketplaceRepository repository;
    private final CredencialMarketplaceService credencialService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    @Value("${siscomercial.mercado-livre.client-id:}") private String clientId;
    @Value("${siscomercial.mercado-livre.client-secret:}") private String clientSecret;
    @Value("${siscomercial.mercado-livre.redirect-uri:}") private String redirectUri;

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
    private void exigirConfiguracaoOAuth() { if (clientId.isBlank() || clientSecret.isBlank() || redirectUri.isBlank()) throw new RegraNegocioException("Configure as variaveis de ambiente do Mercado Livre no backend."); }
    private String codificar(String valor) { return URLEncoder.encode(valor, StandardCharsets.UTF_8); }
}
