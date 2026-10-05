package com.siscomercial.ecommerce.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siscomercial.ecommerce.config.AmazonProperties;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.IntegracaoMarketplace;
import com.siscomercial.ecommerce.model.Marketplace;
import com.siscomercial.ecommerce.model.Produto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Publica ofertas de produtos já existentes no catálogo Amazon pela Listings Items API. */
@Component
public class AmazonMarketplaceGateway implements MarketplaceGateway {
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
            String path = "/listings/2021-08-01/items/" + segment(properties.getSellerId()) + "/"
                    + segment(produto.getCodigoInterno()) + "?marketplaceIds=" + segment(properties.getMarketplaceId())
                    + "&requirements=LISTING_OFFER_ONLY&issueLocale=pt_BR";
            HttpRequest request = apiClient.spApiRequest(properties, path, accessToken)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(montarPayload(asin, price,
                            produto.getQuantidadeDisponivel())))).build();
            HttpResponse<String> response = apiClient.sendSpApi(request);
            JsonNode json = response.body() == null || response.body().isBlank()
                    ? objectMapper.createObjectNode() : objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new RegraNegocioException("Amazon SP-API recusou a publicação (HTTP " + response.statusCode() + "). "
                        + mensagemIssues(json));
            String status = json.path("status").asText("");
            if (!"ACCEPTED".equalsIgnoreCase(status))
                throw new RegraNegocioException(status.isBlank()
                        ? "Amazon SP-API retornou uma resposta de publicação incompleta."
                        : "Amazon SP-API não aceitou a publicação. " + mensagemIssues(json));
            String submissionId = json.path("submissionId").asText("");
            if (submissionId.isBlank())
                throw new RegraNegocioException("Amazon SP-API aceitou a solicitação, mas não retornou o identificador da publicação.");
            return new ResultadoPublicacao(submissionId, null, "accepted");
        } catch (RegraNegocioException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RegraNegocioException("Falha ao publicar na Amazon SP-API.");
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

    private String mensagemIssues(JsonNode json) {
        JsonNode issues = json.path("issues");
        if (!issues.isArray() || issues.isEmpty()) return "";
        StringBuilder mensagem = new StringBuilder();
        for (JsonNode issue : issues) {
            String text = issue.path("message").asText("");
            if (!text.isBlank()) {
                if (mensagem.length() > 0) mensagem.append("; ");
                mensagem.append(text);
            }
        }
        return mensagem.toString();
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
