package com.siscomercial.ecommerce.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.IntegracaoMarketplace;
import com.siscomercial.ecommerce.model.Marketplace;
import com.siscomercial.ecommerce.model.Produto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapter da Meta Graph API para Product Catalog/Product Item.
 *
 * O gateway utiliza somente a API oficial. Nao existe automacao de navegador,
 * scraping ou acesso a telas do Facebook.
 */
@Component
public class MetaMarketplaceGateway implements MarketplaceGateway {
    private static final String BASE_URL = "https://graph.facebook.com";
    private static final String PRODUCT_FIELDS =
            "id,retailer_id,name,status,visibility,availability,inventory,quantity_to_sell_on_facebook,url";

    private final CredencialMarketplaceService credencialService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String graphApiVersion;
    private final String publicBaseUrl;

    @Autowired
    public MetaMarketplaceGateway(
            CredencialMarketplaceService credencialService,
            ObjectMapper objectMapper,
            @Value("${siscomercial.meta.graph-api-version:v24.0}") String graphApiVersion,
            @Value("${siscomercial.catalogo.public-base-url:${siscomercial.upload.public-base-url:http://localhost:8080}}") String publicBaseUrl) {
        this(credencialService, objectMapper, graphApiVersion, publicBaseUrl,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    MetaMarketplaceGateway(
            CredencialMarketplaceService credencialService,
            ObjectMapper objectMapper,
            String graphApiVersion,
            String publicBaseUrl,
            HttpClient httpClient) {
        this.credencialService = credencialService;
        this.objectMapper = objectMapper;
        this.graphApiVersion = normalizarVersao(graphApiVersion);
        this.publicBaseUrl = removerBarraFinal(publicBaseUrl);
        this.httpClient = httpClient;
    }

    @Override
    public Marketplace marketplace() {
        return Marketplace.META;
    }

    @Override
    public ResultadoPublicacao publicar(IntegracaoMarketplace integracao, Produto produto) {
        validarPublicacao(integracao, produto);
        try {
            String token = revelarToken(integracao);
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("retailer_id", produto.getCodigoInterno());
            corpo.put("name", produto.getNome());
            corpo.put("description", produto.getDescricao() == null ? produto.getNome() : produto.getDescricao());
            corpo.put("price", precoEmCentavos(produto));
            corpo.put("currency", "BRL");
            corpo.put("availability", produto.getQuantidadeDisponivel() > 0 ? "in stock" : "out of stock");
            corpo.put("condition", "new");
            corpo.put("inventory", produto.getQuantidadeDisponivel());
            corpo.put("image_url", imagemPublica(produto));
            corpo.put("url", urlProduto(produto));
            corpo.put("allow_upsert", true);

            JsonNode resposta = executarPost("/" + integracao.getIdentificadorExterno() + "/products", token,
                    corpo, "publicar o produto no catalogo Meta");
            String id = resposta.path("id").asText("");
            if (id.isBlank()) {
                throw new RegraNegocioException("A Meta nao retornou o identificador do produto publicado.");
            }
            return new ResultadoPublicacao(id, resposta.path("url").asText(urlProduto(produto)),
                    resposta.path("visibility").asText("published"));
        } catch (RegraNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new RegraNegocioException("Falha ao publicar na Meta: " + e.getMessage());
        }
    }

    @Override
    public ResultadoOperacao encerrar(IntegracaoMarketplace integracao, String identificadorExterno) {
        validarIntegracao(integracao);
        if (identificadorExterno == null || identificadorExterno.isBlank()) {
            throw new RegraNegocioException("Identificador da publicacao Meta obrigatorio.");
        }
        try {
            String token = revelarToken(integracao);
            Map<String, Object> corpo = Map.of("visibility", "hidden");
            executarPost("/" + encodePath(identificadorExterno), token, corpo,
                    "encerrar a publicacao na Meta");
            return new ResultadoOperacao("hidden");
        } catch (RegraNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new RegraNegocioException("Falha ao encerrar a publicacao na Meta: " + e.getMessage());
        }
    }

    @Override
    public ResultadoSincronizacao sincronizar(IntegracaoMarketplace integracao, String identificadorExterno) {
        validarIntegracao(integracao);
        try {
            String token = revelarToken(integracao);
            URI uri = URI.create(BASE_URL + "/" + graphApiVersion + "/" + encodePath(identificadorExterno)
                    + "?fields=" + encodeQuery(PRODUCT_FIELDS));
            JsonNode resposta = executar("GET", uri, token, null, "sincronizar a publicacao na Meta");
            String visibility = resposta.path("visibility").asText("");
            String status = resposta.path("status").asText("");
            String disponibilidade = resposta.path("availability").asText("");
            int estoque = resposta.path("inventory").asInt(
                    resposta.path("quantity_to_sell_on_facebook").asInt(0));
            if ("hidden".equalsIgnoreCase(visibility)) {
                status = "hidden";
            } else if (status.isBlank()) {
                status = disponibilidade;
            }
            return new ResultadoSincronizacao(status.isBlank() ? "active" : status, estoque,
                    resposta.path("url").asText(null));
        } catch (RegraNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new RegraNegocioException("Falha ao sincronizar a publicacao na Meta: " + e.getMessage());
        }
    }

    private void validarPublicacao(IntegracaoMarketplace integracao, Produto produto) {
        validarIntegracao(integracao);
        if (produto == null) throw new RegraNegocioException("Produto obrigatorio para publicacao na Meta.");
        if (produto.getNome() == null || produto.getNome().isBlank()) {
            throw new RegraNegocioException("O produto deve possuir nome para ser publicado na Meta.");
        }
        if (produto.getCodigoInterno() == null || produto.getCodigoInterno().isBlank()) {
            throw new RegraNegocioException("O produto deve possuir codigo interno para ser publicado na Meta.");
        }
        if (preco(produto) == null || preco(produto).signum() <= 0) {
            throw new RegraNegocioException("O produto precisa possuir preco de venda valido para ser publicado na Meta.");
        }
        if (produto.getQuantidadeDisponivel() < 0) {
            throw new RegraNegocioException("O produto possui estoque disponivel invalido para publicacao na Meta.");
        }
        if (imagemPublica(produto) == null) {
            throw new RegraNegocioException("O produto precisa possuir pelo menos uma imagem publica HTTP/HTTPS para publicar na Meta.");
        }
        if (publicBaseUrl.isBlank() || !publicBaseUrl.startsWith("http")) {
            throw new RegraNegocioException("Configure siscomercial.catalogo.public-base-url com uma URL publica HTTP/HTTPS antes de publicar na Meta.");
        }
    }

    private void validarIntegracao(IntegracaoMarketplace integracao) {
        if (integracao == null || integracao.getMarketplace() != Marketplace.META) {
            throw new RegraNegocioException("A integracao informada nao pertence a Meta.");
        }
        if (integracao.getIdentificadorExterno() == null || integracao.getIdentificadorExterno().isBlank()) {
            throw new RegraNegocioException("Informe o ID do catalogo Meta na integracao.");
        }
        if (integracao.getTokenProtegido() == null || integracao.getTokenProtegido().isBlank()) {
            throw new RegraNegocioException("A integracao Meta ainda nao possui credencial configurada.");
        }
    }

    private String revelarToken(IntegracaoMarketplace integracao) {
        String token = credencialService.revelar(integracao.getTokenProtegido());
        if (token == null || token.isBlank()) {
            throw new RegraNegocioException("A integracao Meta nao possui token valido.");
        }
        return token;
    }

    private JsonNode executarPost(String path, String token, Map<String, Object> corpo, String operacao) throws Exception {
        URI uri = URI.create(BASE_URL + "/" + graphApiVersion + path);
        return executar("POST", uri, token, objectMapper.writeValueAsString(corpo), operacao);
    }

    private JsonNode executar(String metodo, URI uri, String token, String corpo, String operacao) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");
        if ("POST".equals(metodo)) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corpo == null ? "{}" : corpo));
        } else {
            builder.GET();
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode json = response.body() == null || response.body().isBlank()
                ? objectMapper.createObjectNode() : objectMapper.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || json.has("error")) {
            throw new RegraNegocioException(mensagemErroMeta(response.statusCode(), json, operacao));
        }
        return json;
    }

    private String mensagemErroMeta(int statusCode, JsonNode json, String operacao) {
        JsonNode erro = json.path("error");
        String mensagem = erro.path("message").asText("");
        if (mensagem.isBlank()) mensagem = json.path("message").asText("");
        if (mensagem.isBlank()) mensagem = "HTTP " + statusCode;
        return "Nao foi possivel " + operacao + ". A conta/catalogo Meta pode nao estar elegivel ou nao possuir as permissoes necessarias: " + mensagem;
    }

    private BigDecimal preco(Produto produto) {
        return produto.isEmPromocao() ? produto.getPrecoPromocional() : produto.getPrecoVenda();
    }

    private long precoEmCentavos(Produto produto) {
        return preco(produto).movePointRight(2).longValueExact();
    }

    private String imagemPublica(Produto produto) {
        if (produto.getImagemPrincipal() != null && produto.getImagemPrincipal().startsWith("http")) {
            return produto.getImagemPrincipal();
        }
        if (produto.getImagens() != null) {
            return produto.getImagens().stream().filter(u -> u != null && u.startsWith("http")).findFirst().orElse(null);
        }
        return null;
    }

    private String urlProduto(Produto produto) {
        return publicBaseUrl + "/catalogo/produtos/" + encodePath(produto.getCodigoInterno());
    }

    private String encodePath(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String encodeQuery(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    private static String normalizarVersao(String valor) {
        String versao = valor == null ? "v24.0" : valor.trim();
        if (versao.isBlank()) return "v24.0";
        return versao.startsWith("v") ? versao : "v" + versao;
    }

    private static String removerBarraFinal(String valor) {
        String base = valor == null ? "" : valor.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }
}
