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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Adapter oficial do Mercado Livre. Nenhum token sai deste processo de backend. */
@Component
@RequiredArgsConstructor
public class MercadoLivreMarketplaceGateway implements MarketplaceGateway {
    private static final URI ITEMS_URI = URI.create("https://api.mercadolibre.com/items");
    private final CredencialMarketplaceService credencialService;
    private final MercadoLivreCategoriaService categoriaService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public Marketplace marketplace() {
        return Marketplace.MERCADO_LIVRE;
    }

    @Override
    public ResultadoPublicacao publicar(IntegracaoMarketplace integracao, Produto produto) {
        validarProduto(produto);

        try {
            String token = credencialService.revelar(integracao.getTokenProtegido());
            validarAtributosObrigatorios(integracao, produto);
            Map<String, Object> corpo = montarCorpoItem(produto);

            JsonNode item = executarPost(ITEMS_URI, token, corpo, "publicar o produto no Mercado Livre");
            String itemId = item.path("id").asText();
            if (itemId.isBlank()) {
                throw new RegraNegocioException("Mercado Livre nao retornou o identificador da publicacao.");
            }

            publicarDescricao(itemId, produto.getDescricao(), token);

            String permalink = item.path("permalink").asText(null);
            String status = item.path("status").asText("active");
            return new ResultadoPublicacao(itemId, permalink, status);
        } catch (RegraNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new RegraNegocioException("Falha ao publicar no Mercado Livre: " + e.getMessage());
        }
    }

    private void validarProduto(Produto produto) {
        if (produto.getNome() == null || produto.getNome().isBlank()) {
            throw new RegraNegocioException("O produto deve possuir nome para ser publicado.");
        }
        if (produto.getCategoriaMercadoLivreId() == null || !produto.getCategoriaMercadoLivreId().matches("MLB\\d+")) {
            throw new RegraNegocioException("O produto deve possuir uma categoria valida do Mercado Livre (ex.: MLB123456). Use o preditor de categorias antes de publicar.");
        }
        if (produto.getQuantidadeDisponivel() <= 0) {
            throw new RegraNegocioException("O produto precisa ter estoque disponivel para ser publicado.");
        }
        BigDecimal preco = preco(produto);
        if (preco == null || preco.signum() <= 0) {
            throw new RegraNegocioException("O produto precisa possuir preco de venda valido para ser publicado.");
        }
        if (produto.getImagens() == null || produto.getImagens().stream().noneMatch(this::urlValida)) {
            throw new RegraNegocioException(
                    "O produto precisa possuir pelo menos uma imagem publica (URL http/https) para ser publicado no Mercado Livre."
            );
        }
    }

    private Map<String, Object> montarCorpoItem(Produto produto) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("title", produto.getNome());
        corpo.put("category_id", produto.getCategoriaMercadoLivreId());
        corpo.put("price", preco(produto));
        corpo.put("currency_id", "BRL");
        corpo.put("available_quantity", produto.getQuantidadeDisponivel());
        corpo.put("buying_mode", "buy_it_now");
        corpo.put("listing_type_id", "gold_special");
        corpo.put("condition", "new");
        corpo.put("pictures", montarImagens(produto));
        corpo.put("attributes", montarAtributos(produto));
        return corpo;
    }

    private void validarAtributosObrigatorios(IntegracaoMarketplace integracao, Produto produto) {
        List<MercadoLivreCategoriaService.AtributoCategoria> atributosCategoria =
                categoriaService.listarAtributos(integracao, produto.getCategoriaMercadoLivreId());

        for (MercadoLivreCategoriaService.AtributoCategoria atributo : atributosCategoria) {
            JsonNode tags = atributo.tags();
            boolean obrigatorio = tags != null && (tags.path("required").asBoolean(false)
                    || tags.path("new_required").asBoolean(false));
            if (!obrigatorio) {
                continue;
            }

            ProdutoAtributoEncontrado encontrado = encontrarAtributo(produto, atributo.id());
            if (encontrado == null || (vazio(encontrado.valueId()) && vazio(encontrado.valueName()))) {
                throw new RegraNegocioException(
                        "Preencha o atributo obrigatorio do Mercado Livre: " + atributo.name() + "."
                );
            }
        }
    }

    private ProdutoAtributoEncontrado encontrarAtributo(Produto produto, String atributoId) {
        if (produto.getAtributosMercadoLivre() == null) {
            return null;
        }
        return produto.getAtributosMercadoLivre().stream()
                .filter(item -> atributoId.equals(item.getAtributoId()))
                .map(item -> new ProdutoAtributoEncontrado(item.getValueId(), item.getValueName()))
                .findFirst()
                .orElse(null);
    }

    private List<Map<String, Object>> montarAtributos(Produto produto) {
        List<Map<String, Object>> atributos = new ArrayList<>();
        if (produto.getAtributosMercadoLivre() == null) {
            return atributos;
        }

        produto.getAtributosMercadoLivre().stream()
                .filter(item -> !vazio(item.getAtributoId()))
                .filter(item -> !vazio(item.getValueId()) || !vazio(item.getValueName()))
                .forEach(item -> {
                    Map<String, Object> atributo = new LinkedHashMap<>();
                    atributo.put("id", item.getAtributoId());
                    if (!vazio(item.getValueId())) {
                        atributo.put("value_id", item.getValueId());
                    }
                    if (!vazio(item.getValueName())) {
                        atributo.put("value_name", item.getValueName());
                    }
                    atributos.add(atributo);
                });
        return atributos;
    }

    private boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private record ProdutoAtributoEncontrado(String valueId, String valueName) {}

    private List<Map<String, String>> montarImagens(Produto produto) {
        List<Map<String, String>> imagens = new ArrayList<>();
        List<String> urls = new ArrayList<>();

        if (urlValida(produto.getImagemPrincipal())) {
            urls.add(produto.getImagemPrincipal());
        }
        if (produto.getImagens() != null) {
            produto.getImagens().stream()
                    .filter(this::urlValida)
                    .filter(url -> !urls.contains(url))
                    .forEach(urls::add);
        }

        urls.stream().limit(6).forEach(url -> {
            Map<String, String> imagem = new LinkedHashMap<>();
            imagem.put("source", url);
            imagens.add(imagem);
        });
        return imagens;
    }

    private void publicarDescricao(String itemId, String descricao, String token) throws Exception {
        if (descricao == null || descricao.isBlank()) {
            return;
        }

        Map<String, String> corpo = Map.of("plain_text", descricao);
        URI uri = URI.create(ITEMS_URI + "/" + itemId + "/description");
        executarPost(uri, token, corpo, "enviar a descricao do produto ao Mercado Livre");
    }

    private JsonNode executarPost(URI uri, String token, Object corpo, String operacao) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(corpo)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = response.body() == null || response.body().isBlank()
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(response.body());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RegraNegocioException(mensagemErroMercadoLivre(response.statusCode(), json, operacao));
        }
        return json;
    }

    private String mensagemErroMercadoLivre(int statusCode, JsonNode json, String operacao) {
        String message = json.path("message").asText("");
        String error = json.path("error").asText("");
        StringBuilder detalhe = new StringBuilder();
        if (!message.isBlank()) detalhe.append(message);
        if (!error.isBlank() && !error.equals(message)) {
            if (detalhe.length() > 0) detalhe.append(" | ");
            detalhe.append(error);
        }
        JsonNode causes = json.path("cause");
        if (causes.isArray() && !causes.isEmpty()) {
            for (JsonNode cause : causes) {
                String causeMessage = cause.path("cause_message").asText(cause.path("message").asText(""));
                String causeId = cause.path("cause_id").asText("");
                if (!causeMessage.isBlank() || !causeId.isBlank()) {
                    if (detalhe.length() > 0) detalhe.append(" | ");
                    detalhe.append("causa");
                    if (!causeId.isBlank()) detalhe.append(" ").append(causeId);
                    if (!causeMessage.isBlank()) detalhe.append(": ").append(causeMessage);
                }
            }
        }
        if (detalhe.length() == 0) detalhe.append("erro sem detalhes");
        return "Mercado Livre recusou a solicitacao para " + operacao + " (HTTP " + statusCode + "): " + detalhe;
    }

    private boolean urlValida(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url.trim());
            return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private BigDecimal preco(Produto produto) {
        return produto.isEmPromocao() ? produto.getPrecoPromocional() : produto.getPrecoVenda();
    }
}
