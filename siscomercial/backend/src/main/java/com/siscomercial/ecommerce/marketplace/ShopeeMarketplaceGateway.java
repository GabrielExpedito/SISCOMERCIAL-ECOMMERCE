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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter oficial da Shopee Open Platform v2.
 */
@Component
public class ShopeeMarketplaceGateway implements MarketplaceGateway {
    private static final String BASE_URL = "https://partner.shopeemobile.com";
    private static final String PRODUCT_PATH = "/api/v2/product/add_item";
    private static final String UPDATE_ITEM_PATH = "/api/v2/product/update_item";
    private static final String GET_ITEM_PATH = "/api/v2/product/get_item_base_info";
    private static final String MEDIA_UPLOAD_PATH = "/api/v2/media_space/upload_image";

    private final CredencialMarketplaceService credencialService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String partnerId;
    private final String partnerKey;
    private final long logisticId;

    @Autowired
    public ShopeeMarketplaceGateway(
            CredencialMarketplaceService credencialService,
            ObjectMapper objectMapper,
            @Value("${siscomercial.shopee.partner-id:}") String partnerId,
            @Value("${siscomercial.shopee.partner-key:}") String partnerKey,
            @Value("${siscomercial.shopee.logistic-id:0}") long logisticId) {
        this(credencialService, objectMapper, partnerId, partnerKey, logisticId,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    ShopeeMarketplaceGateway(
            CredencialMarketplaceService credencialService,
            ObjectMapper objectMapper,
            String partnerId,
            String partnerKey,
            long logisticId,
            HttpClient httpClient) {
        this.credencialService = credencialService;
        this.objectMapper = objectMapper;
        this.partnerId = partnerId;
        this.partnerKey = partnerKey;
        this.logisticId = logisticId;
        this.httpClient = httpClient;
    }

    @Override
    public Marketplace marketplace() {
        return Marketplace.SHOPEE;
    }

    @Override
    public ResultadoPublicacao publicar(IntegracaoMarketplace integracao, Produto produto) {
        validarConfiguracao(integracao, produto);
        try {
            String accessToken = credencialService.revelar(integracao.getTokenProtegido());
            List<String> imageIds = enviarImagens(produto, accessToken, integracao.getIdentificadorExterno());

            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("item_name", produto.getNome());
            corpo.put("description", produto.getDescricao() == null ? produto.getNome() : produto.getDescricao());
            corpo.put("category_id", Long.parseLong(produto.getCategoriaShopeeId()));
            corpo.put("original_price", preco(produto));
            corpo.put("weight", produto.getPesoShopeeKg().doubleValue());
            corpo.put("item_sku", produto.getCodigoInterno());
            corpo.put("condition", "NEW");
            corpo.put("item_status", "NORMAL");
            corpo.put("image", Map.of("image_id_list", imageIds));
            corpo.put("logistic_info", List.of(Map.of("logistic_id", logisticId, "enabled", true)));
            corpo.put("seller_stock", List.of(Map.of("stock", produto.getQuantidadeDisponivel())));
            corpo.put("pre_order", Map.of("is_pre_order", false));

            JsonNode resposta = executarPost(PRODUCT_PATH, accessToken, integracao.getIdentificadorExterno(), corpo,
                    "publicar o produto na Shopee");
            JsonNode response = resposta.path("response");
            long itemId = response.path("item_id").asLong(0);
            if (itemId == 0) {
                itemId = resposta.path("item_id").asLong(0);
            }
            if (itemId == 0) {
                throw new RegraNegocioException("A Shopee nao retornou o identificador da publicacao.");
            }
            return new ResultadoPublicacao(String.valueOf(itemId), null, "NORMAL");
        } catch (RegraNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new RegraNegocioException("Falha ao publicar na Shopee: " + e.getMessage());
        }
    }

    @Override
    public ResultadoOperacao encerrar(IntegracaoMarketplace integracao, String identificadorExterno) {
        validarIntegracao(integracao);
        try {
            String token = credencialService.revelar(integracao.getTokenProtegido());
            Map<String, Object> corpo = Map.of(
                    "item_id", Long.parseLong(identificadorExterno),
                    "item_status", "UNLIST"
            );
            executarPost(UPDATE_ITEM_PATH, token, integracao.getIdentificadorExterno(), corpo,
                    "encerrar a publicacao na Shopee");
            return new ResultadoOperacao("UNLIST");
        } catch (RegraNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new RegraNegocioException("Falha ao encerrar a publicacao na Shopee: " + e.getMessage());
        }
    }

    @Override
    public ResultadoSincronizacao sincronizar(IntegracaoMarketplace integracao, String identificadorExterno) {
        validarIntegracao(integracao);
        try {
            String token = credencialService.revelar(integracao.getTokenProtegido());
            URI uri = URI.create(urlComum(GET_ITEM_PATH, token, integracao.getIdentificadorExterno())
                    + "&item_id_list=" + Long.parseLong(identificadorExterno));
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode resposta = validarResposta(response, "consultar a publicacao na Shopee");
            JsonNode itens = resposta.path("response").path("item_list");
            if (!itens.isArray() || itens.isEmpty()) {
                throw new RegraNegocioException("A Shopee nao retornou a publicacao " + identificadorExterno + ".");
            }
            JsonNode item = itens.get(0);
            String status = item.path("item_status").asText("UNKNOWN");
            int estoque = item.path("stock_info").path("stock_list").path(0).path("normal_stock").asInt(
                    item.path("stock_info_v2").path("seller_stock").path(0).path("stock").asInt(0));
            return new ResultadoSincronizacao(status, estoque, null);
        } catch (RegraNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new RegraNegocioException("Falha ao sincronizar a publicacao na Shopee: " + e.getMessage());
        }
    }

    private void validarConfiguracao(IntegracaoMarketplace integracao, Produto produto) {
        validarIntegracao(integracao);
        if (produto == null) throw new RegraNegocioException("Produto obrigatorio para publicacao na Shopee.");
        if (produto.getCategoriaShopeeId() == null || produto.getCategoriaShopeeId().isBlank()) {
            throw new RegraNegocioException("Informe a categoria Shopee do produto antes de publicar.");
        }
        try {
            Long.parseLong(produto.getCategoriaShopeeId());
        } catch (NumberFormatException e) {
            throw new RegraNegocioException("A categoria Shopee deve possuir um identificador numerico.");
        }
        if (produto.getPesoShopeeKg() == null || produto.getPesoShopeeKg().signum() <= 0) {
            throw new RegraNegocioException("Informe o peso do produto em quilogramas para publicar na Shopee.");
        }
        if (logisticId <= 0) {
            throw new RegraNegocioException("Configure siscomercial.shopee.logistic-id antes de publicar na Shopee.");
        }
        if (produto.getQuantidadeDisponivel() <= 0) {
            throw new RegraNegocioException("O produto nao possui estoque disponivel para publicacao na Shopee.");
        }

        boolean possuiImagemPublica = produto.getImagemPrincipal() != null
                && produto.getImagemPrincipal().startsWith("http");

        if (!possuiImagemPublica && produto.getImagens() != null) {
            possuiImagemPublica = produto.getImagens().stream()
                    .anyMatch(u -> u != null && u.startsWith("http"));
        }

        if (!possuiImagemPublica) {
            throw new RegraNegocioException(
                    "O produto precisa possuir ao menos uma imagem publica HTTP/HTTPS para publicar na Shopee."
            );
        }
    }

    private void validarIntegracao(IntegracaoMarketplace integracao) {
        if (integracao == null || integracao.getMarketplace() != Marketplace.SHOPEE) {
            throw new RegraNegocioException("A integracao informada nao pertence a Shopee.");
        }
        if (integracao.getTokenProtegido() == null || integracao.getTokenProtegido().isBlank()) {
            throw new RegraNegocioException("A integracao Shopee ainda nao possui autorizacao valida.");
        }
        if (partnerId.isBlank() || partnerKey.isBlank()) {
            throw new RegraNegocioException("Configure SHOPEE_PARTNER_ID e SHOPEE_PARTNER_KEY no backend.");
        }
        if (integracao.getIdentificadorExterno() == null || integracao.getIdentificadorExterno().isBlank()) {
            throw new RegraNegocioException("A integracao Shopee nao possui shop_id.");
        }
    }

    private BigDecimal preco(Produto produto) {
        return produto.isEmPromocao() ? produto.getPrecoPromocional() : produto.getPrecoVenda();
    }

    private List<String> enviarImagens(Produto produto, String token, String shopId) throws Exception {
        List<String> urls = new ArrayList<>();
        if (produto.getImagemPrincipal() != null) urls.add(produto.getImagemPrincipal());
        if (produto.getImagens() != null) urls.addAll(produto.getImagens());
        urls = urls.stream().filter(u -> u != null && u.startsWith("http")).distinct().limit(9).toList();
        List<String> ids = new ArrayList<>();
        for (String url : urls) {
            byte[] conteudo = baixarImagem(url);
            JsonNode resposta = enviarImagem(conteudo, token, shopId);
            JsonNode infos = resposta.path("response").path("image_info_list");
            if (!infos.isArray() || infos.isEmpty()) {
                throw new RegraNegocioException("A Shopee nao retornou o image_id para a imagem do produto.");
            }
            String id = infos.get(0).path("image_info").path("image_id").asText("");
            if (id.isBlank()) throw new RegraNegocioException("A Shopee nao retornou um image_id valido.");
            ids.add(id);
        }
        return ids;
    }

    private byte[] baixarImagem(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RegraNegocioException("Nao foi possivel baixar a imagem publica do produto (HTTP " + response.statusCode() + ").");
        }
        if (response.body() == null || response.body().length == 0)
            throw new RegraNegocioException("A imagem publica do produto esta vazia.");
        if (response.body().length > 10 * 1024 * 1024)
            throw new RegraNegocioException("A imagem do produto excede o limite de 10 MB da Shopee.");
        return response.body();
    }

    private JsonNode enviarImagem(byte[] imagem, String token, String shopId) throws Exception {
        String boundary = "----SisComercialShopee" + System.currentTimeMillis();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"image\"; filename=\"produto.jpg\"\r\n");
        write(out, "Content-Type: image/jpeg\r\n\r\n");
        out.write(imagem);
        write(out, "\r\n--" + boundary + "--\r\n");
        URI uri = URI.create(urlPublico(MEDIA_UPLOAD_PATH));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return validarResposta(response, "enviar imagem para a Shopee");
    }

    private JsonNode executarPost(String path, String token, String shopId, Object corpo, String operacao) throws Exception {
        URI uri = URI.create(urlComum(path, token, shopId));
        String body = objectMapper.writeValueAsString(corpo);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return validarResposta(response, operacao);
    }

    private JsonNode validarResposta(HttpResponse<String> response, String operacao) throws IOException {
        JsonNode json = response.body() == null || response.body().isBlank()
                ? objectMapper.createObjectNode() : objectMapper.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RegraNegocioException("Falha ao " + operacao + " (HTTP " + response.statusCode() + "): "
                    + json.path("message").asText(json.path("error").asText("erro sem detalhes")));
        }
        String error = json.path("error").asText("");
        if (!error.isBlank()) {
            throw new RegraNegocioException("Shopee recusou a operacao: " + error + " - "
                    + json.path("message").asText("sem detalhes"));
        }
        return json;
    }

    private String urlPublico(String path) {
        long timestamp = Instant.now().getEpochSecond();
        String sign = assinar(path, timestamp, partnerId, partnerKey, "", "");
        return BASE_URL + path + "?partner_id=" + partnerId + "&timestamp=" + timestamp + "&sign=" + sign;
    }

    private String urlComum(String path, String token, String shopId) {
        long timestamp = Instant.now().getEpochSecond();
        String sign = assinar(path, timestamp, token, shopId);
        return BASE_URL + path + "?partner_id=" + partnerId + "&timestamp=" + timestamp
                + "&access_token=" + codificar(token) + "&shop_id=" + codificar(shopId) + "&sign=" + sign;
    }

    static String assinar(String path, long timestamp, String partnerId, String partnerKey, String accessToken,
                          String shopId) {
        try {
            String base = partnerId + path + timestamp + accessToken + shopId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(partnerKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormatUtil.hex(mac.doFinal(base.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RegraNegocioException("Nao foi possivel gerar a assinatura da Shopee.");
        }
    }

    private String assinar(String path, long timestamp, String token, String shopId) {
        return assinar(path, timestamp, partnerId, partnerKey, token, shopId);
    }

    private String codificar(String valor) {
        return java.net.URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    private void write(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class HexFormatUtil {
        private static String hex(byte[] bytes) {
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) result.append(String.format("%02x", b));
            return result.toString();
        }
    }
}
