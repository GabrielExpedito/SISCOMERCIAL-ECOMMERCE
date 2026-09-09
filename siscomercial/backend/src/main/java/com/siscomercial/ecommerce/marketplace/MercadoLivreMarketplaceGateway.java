package com.siscomercial.ecommerce.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.IntegracaoMarketplace;
import com.siscomercial.ecommerce.model.Marketplace;
import com.siscomercial.ecommerce.model.Produto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Adapter oficial do Mercado Livre. Nenhum token sai deste processo de backend. */
@Component
@RequiredArgsConstructor
public class MercadoLivreMarketplaceGateway implements MarketplaceGateway {
    private static final URI ITEMS_URI = URI.create("https://api.mercadolibre.com/items");
    private final CredencialMarketplaceService credencialService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override public Marketplace marketplace() { return Marketplace.MERCADO_LIVRE; }

    @Override
    public ResultadoPublicacao publicar(IntegracaoMarketplace integracao, Produto produto) {
        if (produto.getCategoria() == null || produto.getCategoria().isBlank()) {
            throw new RegraNegocioException("O produto deve possuir categoria do Mercado Livre para ser publicado.");
        }
        if (produto.getQuantidadeDisponivel() <= 0) {
            throw new RegraNegocioException("O produto precisa ter estoque disponivel para ser publicado.");
        }
        try {
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("title", produto.getNome());
            corpo.put("category_id", produto.getCategoria());
            corpo.put("price", preco(produto));
            corpo.put("currency_id", "BRL");
            corpo.put("available_quantity", produto.getQuantidadeDisponivel());
            corpo.put("buying_mode", "buy_it_now");
            corpo.put("listing_type_id", "gold_special");
            corpo.put("condition", "new");
            if (produto.getDescricao() != null && !produto.getDescricao().isBlank()) corpo.put("description", produto.getDescricao());
            String token = credencialService.revelar(integracao.getTokenProtegido());
            HttpRequest request = HttpRequest.newBuilder(ITEMS_URI).timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(corpo))).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RegraNegocioException("Mercado Livre recusou a publicacao (HTTP " + response.statusCode()
                        + "): " + json.path("message").asText("erro sem detalhes"));
            }
            String id = json.path("id").asText();
            if (id.isBlank()) throw new RegraNegocioException("Mercado Livre nao retornou o identificador da publicacao.");
            return new ResultadoPublicacao(id, json.path("permalink").asText(null), json.path("status").asText("active"));
        } catch (RegraNegocioException e) { throw e;
        } catch (Exception e) { throw new RegraNegocioException("Falha ao publicar no Mercado Livre: " + e.getMessage()); }
    }

    private BigDecimal preco(Produto produto) {
        return produto.isEmPromocao() ? produto.getPrecoPromocional() : produto.getPrecoVenda();
    }
}
