package com.siscomercial.ecommerce.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.IntegracaoMarketplace;
import com.siscomercial.ecommerce.model.Marketplace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Operacoes de categorizacao e ficha tecnica do Mercado Livre. */
@Service
@RequiredArgsConstructor
public class MercadoLivreCategoriaService {
    private static final String SITE_ID = "MLB";
    private static final String API_BASE = "https://api.mercadolibre.com";

    private final CredencialMarketplaceService credencialService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public List<CategoriaSugestao> preverCategorias(IntegracaoMarketplace integracao, String termo) {
        validarIntegracao(integracao);
        if (termo == null || termo.isBlank()) {
            throw new RegraNegocioException("Informe o nome ou titulo do produto para pesquisar categorias.");
        }

        try {
            String token = credencialService.revelar(integracao.getTokenProtegido());
            String query = URLEncoder.encode(termo.trim(), StandardCharsets.UTF_8);
            URI uri = URI.create(API_BASE + "/sites/" + SITE_ID + "/domain_discovery/search?limit=4&q=" + query);
            JsonNode resposta = executarGet(uri, token, "consultar categorias do Mercado Livre");

            List<CategoriaSugestao> sugestoes = new ArrayList<>();
            if (!resposta.isArray()) {
                return sugestoes;
            }

            for (JsonNode item : resposta) {
                List<AtributoPrevisto> atributos = new ArrayList<>();
                JsonNode atributosNode = item.path("attributes");
                if (atributosNode.isArray()) {
                    for (JsonNode atributo : atributosNode) {
                        atributos.add(new AtributoPrevisto(
                                atributo.path("id").asText(),
                                atributo.path("value_id").asText(null),
                                atributo.path("value_name").asText(null)
                        ));
                    }
                }
                sugestoes.add(new CategoriaSugestao(
                        item.path("domain_id").asText(null),
                        item.path("domain_name").asText(null),
                        item.path("category_id").asText(null),
                        item.path("category_name").asText(null),
                        atributos
                ));
            }
            return sugestoes;
        } catch (RegraNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new RegraNegocioException("Falha ao consultar categorias do Mercado Livre: " + e.getMessage());
        }
    }

    public List<AtributoCategoria> listarAtributos(IntegracaoMarketplace integracao, String categoryId) {
        validarIntegracao(integracao);
        if (categoryId == null || !categoryId.matches("MLB\\d+")) {
            throw new RegraNegocioException("Informe uma categoria Mercado Livre valida (ex.: MLB123456).");
        }

        try {
            String token = credencialService.revelar(integracao.getTokenProtegido());
            URI uri = URI.create(API_BASE + "/categories/" + categoryId + "/attributes");
            JsonNode resposta = executarGet(uri, token, "consultar atributos da categoria " + categoryId);
            List<AtributoCategoria> atributos = new ArrayList<>();

            if (!resposta.isArray()) {
                return atributos;
            }

            for (JsonNode atributo : resposta) {
                atributos.add(new AtributoCategoria(
                        atributo.path("id").asText(),
                        atributo.path("name").asText(),
                        atributo.path("value_type").asText(null),
                        atributo.path("tags"),
                        atributo.path("values")
                ));
            }
            return atributos;
        } catch (RegraNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new RegraNegocioException("Falha ao consultar atributos da categoria " + categoryId + ": " + e.getMessage());
        }
    }

    private JsonNode executarGet(URI uri, String token, String operacao) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = response.body() == null || response.body().isBlank()
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(response.body());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = json.path("message").asText(json.path("error").asText("erro sem detalhes"));
            throw new RegraNegocioException("Mercado Livre recusou a solicitacao para " + operacao
                    + " (HTTP " + response.statusCode() + "): " + message);
        }
        return json;
    }

    private void validarIntegracao(IntegracaoMarketplace integracao) {
        if (integracao == null || integracao.getMarketplace() != Marketplace.MERCADO_LIVRE) {
            throw new RegraNegocioException("A integracao informada nao e do Mercado Livre.");
        }
        if (integracao.getTokenProtegido() == null || integracao.getTokenProtegido().isBlank()) {
            throw new RegraNegocioException("Conclua a autorizacao OAuth do Mercado Livre antes de consultar categorias.");
        }
    }

    public record CategoriaSugestao(
            String domainId,
            String domainName,
            String categoryId,
            String categoryName,
            List<AtributoPrevisto> atributos
    ) {}

    public record AtributoPrevisto(String id, String valueId, String valueName) {}

    public record AtributoCategoria(
            String id,
            String name,
            String valueType,
            JsonNode tags,
            JsonNode values
    ) {}
}
