package com.siscomercial.ecommerce.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siscomercial.ecommerce.config.AmazonProperties;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Obtém um access token LWA em memória usando o refresh token configurado no servidor.
 */
@Service
public class AmazonAuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(AmazonAuthenticationService.class);
    private final AmazonProperties properties;
    private final AmazonApiClient apiClient;
    private final ObjectMapper objectMapper;

    public AmazonAuthenticationService(AmazonProperties properties, AmazonApiClient apiClient,
                                       ObjectMapper objectMapper) {
        this.properties = properties;
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
    }

    public String obterAccessToken() {
        if (!properties.hasLwaCredentials()) {
            throw new RegraNegocioException("Credenciais LWA da Amazon não configuradas no servidor.");
        }
        try {
            HttpResponse<String> response = apiClient.postForm(properties.getLwaTokenUrl(), Map.of(
                    "grant_type", "refresh_token",
                    "refresh_token", properties.getRefreshToken(),
                    "client_id", properties.getClientId(),
                    "client_secret", properties.getClientSecret()
            ));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String diagnostic = AmazonDiagnosticSupport.httpError("LWA", response, objectMapper, properties, null);
                log.warn("Amazon authentication failed: {}", diagnostic);
                throw new RegraNegocioException(diagnostic);
            }
            JsonNode json = objectMapper.readTree(response.body());
            String accessToken = json.path("access_token").asText("");
            if (accessToken.isBlank()) {
                throw new RegraNegocioException("Amazon LWA retornou uma resposta de autenticação inválida.");
            }
            return accessToken;
        } catch (RegraNegocioException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RegraNegocioException("LWA_COMMUNICATION: requisição interrompida.", ex);
        } catch (IOException ex) {
            throw new RegraNegocioException("LWA_COMMUNICATION: falha de rede ao obter token.", ex);
        } catch (Exception ex) {
            throw new RegraNegocioException("LWA_RESPONSE: resposta de autenticação inválida.", ex);
        }
    }
}
