package com.siscomercial.ecommerce.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siscomercial.ecommerce.config.AmazonProperties;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.IntegracaoMarketplace;
import com.siscomercial.ecommerce.model.Marketplace;
import com.siscomercial.ecommerce.model.Produto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Publica ofertas de produtos já existentes no catálogo Amazon pela Listings Items API. */
@Component
public class AmazonMarketplaceGateway implements MarketplaceGateway {
    private static final Logger log = LoggerFactory.getLogger(AmazonMarketplaceGateway.class);
    private final AmazonProperties properties;
    private final AmazonAuthenticationService authenticationService;
    private final AmazonApiClient apiClient;
    private final ObjectMapper objectMapper;

    public AmazonMarketplaceGateway(AmazonProperties properties, AmazonAuthenticationService authenticationService,
                                   AmazonApiClient apiClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.authenticationService = authenticationService;
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
    }

    @Override public Marketplace marketplace() { return Marketplace.AMAZON; }

    @Override
    public ResultadoPublicacao publicar(IntegracaoMarketplace integracao, Produto produto) {
        validarConfiguracao();
        if (produto == null) throw new RegraNegocioException("Produto obrigatório para publicar na Amazon.");
        if (produto.getCodigoInterno() == null || produto.getCodigoInterno().isBlank())
            throw new RegraNegocioException("O produto precisa possuir código interno para usar como SKU Amazon.");
        String asin = produto.getAsinAmazon();
        if (asin == null || !asin.matches("[A-Za-z0-9]{10}"))
            throw new RegraNegocioException("Informe um ASIN válido de um produto já existente no catálogo Amazon.");
        BigDecimal price = produto.isEmPromocao() ? produto.getPrecoPromocional() : produto.getPrecoVenda();
        if (price == null || price.signum() <= 0)
            throw new RegraNegocioException("O produto precisa possuir preço de venda válido para publicar na Amazon.");
        if (produto.getQuantidadeDisponivel() <= 0)
            throw new RegraNegocioException("O produto precisa possuir estoque disponível para publicar na Amazon.");

        try {
            String accessToken = authenticationService.obterAccessToken();
            Instant tokenObtainedAt = Instant.now();
            if (accessToken == null || accessToken.isBlank()) {
                log.warn("Amazon SP-API token diagnostic: {}",
                        AmazonDiagnosticSupport.tokenDiagnostic(accessToken, null, properties, tokenObtainedAt, null));
                throw new RegraNegocioException("Amazon LWA retornou um access token vazio; publicação não enviada.");
            }
            String path = "/listings/2021-08-01/items/" + segment(properties.getSellerId()) + "/"
                    + segment(produto.getCodigoInterno()) + "?marketplaceIds=" + segment(properties.getMarketplaceId())
                    + "&issueLocale=pt_BR";
            HttpRequest request = apiClient.spApiRequest(properties, path, accessToken)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(montarPayload(asin, price,
                            produto.getQuantidadeDisponivel())))).build();
            Instant requestAt = Instant.now();
            String sentToken = request.headers().firstValue("x-amz-access-token").orElse(null);
            String tokenDiagnostic = AmazonDiagnosticSupport.tokenDiagnostic(
                    accessToken, sentToken, properties, tokenObtainedAt, requestAt);
            log.info("Amazon SP-API token diagnostic: {}", tokenDiagnostic);
            HttpResponse<String> response = apiClient.sendSpApi(request);
            log.info("Amazon SP-API response diagnostic: host={} sellerId={} marketplaceId={} status={} requestId={} {}",
                    request.uri().getHost(), properties.getSellerId(), properties.getMarketplaceId(),
                    response.statusCode(), AmazonDiagnosticSupport.requestId(response), tokenDiagnostic);

            JsonNode json = null;
            if (response.body() != null && !response.body().isBlank()) {
                try {
                    json = objectMapper.readTree(response.body());
                } catch (Exception ignored) {
                    // Corpo não é JSON válido; prossegue com json nulo sem perder o status HTTP
                }
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String erroDiagnostico = AmazonDiagnosticSupport.httpError("Amazon SP-API", response,
                        objectMapper, properties, accessToken);
                log.warn("Amazon SP-API recusou a chamada de publicação: {}", erroDiagnostico);
                throw new RegraNegocioException(erroDiagnostico);
            }

            if (json == null) {
                throw new RegraNegocioException("Amazon SP-API retornou uma resposta de publicação incompleta.");
            }

            String status = json.path("status").asText("");
            if (!"ACCEPTED".equalsIgnoreCase(status)) {
                String erroDiagnostico = construirMensagemNaoAceito(response, json, status);
                erroDiagnostico = AmazonDiagnosticSupport.redact(erroDiagnostico, properties, accessToken);
                log.warn("Amazon SP-API não aceitou a publicação: {}", erroDiagnostico);
                throw new RegraNegocioException(erroDiagnostico);
            }

            String submissionId = json.path("submissionId").asText("");
            if (submissionId.isBlank())
                throw new RegraNegocioException("Amazon SP-API aceitou a solicitação, mas não retornou o identificador da publicação.");
            return new ResultadoPublicacao(submissionId, null, "accepted");
        } catch (RegraNegocioException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            String message = "SP_API_COMMUNICATION: requisição interrompida.";
            log.warn("{}", message);
            throw new RegraNegocioException(message, ex);
        } catch (IOException ex) {
            String message = "SP_API_COMMUNICATION: falha de rede durante publicação.";
            log.warn("{}", message);
            throw new RegraNegocioException(message, ex);
        } catch (Exception ex) {
            String message = "SP_API_RESPONSE: falha ao interpretar a resposta da publicação.";
            log.warn("{}", message);
            throw new RegraNegocioException(message, ex);
        }
    }

    private void validarConfiguracao() {
        if (!properties.hasLwaCredentials())
            throw new RegraNegocioException("Credenciais LWA da Amazon não configuradas no servidor.");
        if (properties.getSellerId().isBlank() || properties.getMarketplaceId().isBlank())
            throw new RegraNegocioException("Configure AMAZON_SELLER_ID e AMAZON_MARKETPLACE_ID no backend.");
    }

    private Map<String, Object> montarPayload(String asin, BigDecimal price, int quantity) {
        String marketplaceId = properties.getMarketplaceId();
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("condition_type", List.of(Map.of("value", "new_new", "marketplace_id", marketplaceId)));
        attributes.put("merchant_suggested_asin", List.of(Map.of("value", asin, "marketplace_id", marketplaceId)));
        attributes.put("fulfillment_availability", List.of(Map.of("fulfillment_channel_code", "DEFAULT", "quantity", quantity)));
        attributes.put("purchasable_offer", List.of(Map.of("currency", "BRL", "our_price", List.of(
                Map.of("schedule", List.of(Map.of("value_with_tax", price)))), "marketplace_id", marketplaceId)));
        return Map.of("productType", "PRODUCT", "requirements", "LISTING_OFFER_ONLY", "attributes", attributes);
    }

    private String construirMensagemErro(HttpResponse<String> response, JsonNode json) {
        StringBuilder sb = new StringBuilder();
        sb.append("Amazon SP-API recusou a publicação (HTTP ").append(response.statusCode()).append(").");

        String mensagens = extrairMensagensErro(json);
        if (!mensagens.isBlank()) {
            sb.append(" ").append(mensagens);
        }

        String requestId = extrairRequestId(response);
        if (requestId != null && !requestId.isBlank()) {
            sb.append(" (Request ID: ").append(requestId).append(")");
        }

        return sb.toString().trim();
    }

    private String construirMensagemNaoAceito(HttpResponse<String> response, JsonNode json, String status) {
        StringBuilder sb = new StringBuilder();
        if (status == null || status.isBlank()) {
            sb.append("Amazon SP-API retornou uma resposta de publicação incompleta.");
        } else {
            sb.append("Amazon SP-API não aceitou a publicação.");
        }

        String mensagens = extrairMensagensErro(json);
        if (!mensagens.isBlank()) {
            sb.append(" ").append(mensagens);
        }

        String requestId = extrairRequestId(response);
        if (requestId != null && !requestId.isBlank()) {
            sb.append(" (Request ID: ").append(requestId).append(")");
        }

        return sb.toString().trim();
    }

    private String extrairMensagensErro(JsonNode json) {
        if (json == null || json.isEmpty() || json.isMissingNode()) {
            return "";
        }

        Set<String> mensagens = new LinkedHashSet<>();

        // 1. Array 'errors' (modelo padrão SP-API)
        JsonNode errors = json.path("errors");
        if (errors.isArray()) {
            for (JsonNode error : errors) {
                String item = formatarItemErro(
                        error.path("code").asText("").trim(),
                        error.path("message").asText("").trim(),
                        error.path("details").asText("").trim()
                );
                if (!item.isBlank()) {
                    mensagens.add(item);
                }
            }
        }

        // 2. Array 'issues' (modelo da Listings Items API)
        JsonNode issues = json.path("issues");
        if (issues.isArray()) {
            for (JsonNode issue : issues) {
                String item = formatarItemErro(
                        issue.path("code").asText("").trim(),
                        issue.path("message").asText("").trim(),
                        issue.path("details").asText("").trim()
                );
                if (!item.isBlank()) {
                    mensagens.add(item);
                }
            }
        }

        // 3. Fallback: campos de erro diretos na raiz do JSON
        if (mensagens.isEmpty()) {
            String rootMsg = json.path("message").asText("").trim();
            if (rootMsg.isBlank()) rootMsg = json.path("errorMessage").asText("").trim();
            if (rootMsg.isBlank()) rootMsg = json.path("error_description").asText("").trim();

            String rootCode = json.path("code").asText("").trim();
            if (rootCode.isBlank()) rootCode = json.path("error").asText("").trim();

            String rootDetails = json.path("details").asText("").trim();

            String item = formatarItemErro(rootCode, rootMsg, rootDetails);
            if (!item.isBlank()) {
                mensagens.add(item);
            }
        }

        return String.join("; ", mensagens);
    }

    private String formatarItemErro(String code, String message, String details) {
        StringBuilder item = new StringBuilder();
        if (!code.isBlank()) {
            item.append("[").append(code).append("]");
        }

        String principal = message;
        if (principal.isBlank()) {
            principal = details;
            details = "";
        }

        if (!principal.isBlank()) {
            if (item.length() > 0) {
                item.append(" ");
            }
            item.append(principal);
        }

        if (!details.isBlank() && !details.equalsIgnoreCase(principal) && !principal.contains(details)) {
            if (item.length() > 0) {
                if (!item.toString().endsWith(".")) {
                    item.append(".");
                }
                item.append(" Detalhes: ").append(details);
            } else {
                item.append(details);
            }
        }

        return item.toString().trim();
    }

    private String extrairRequestId(HttpResponse<?> response) {
        if (response == null) {
            return null;
        }
        try {
            if (response.headers() == null) {
                return null;
            }
            return response.headers().firstValue("x-amzn-RequestId")
                    .or(() -> response.headers().firstValue("x-amz-request-id"))
                    .or(() -> response.headers().firstValue("x-amzn-requestid"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Override public ResultadoOperacao encerrar(IntegracaoMarketplace integracao, String id) {
        throw new RegraNegocioException("Encerramento de listings Amazon não faz parte desta etapa.");
    }
    @Override public ResultadoSincronizacao sincronizar(IntegracaoMarketplace integracao, String id) {
        throw new RegraNegocioException("Sincronização de listings Amazon não faz parte desta etapa.");
    }
    @Override public List<ResultadoPedido> buscarPedidos(IntegracaoMarketplace integracao, LocalDateTime desde) {
        throw new RegraNegocioException("Consulta de pedidos Amazon não faz parte desta etapa.");
    }
}
