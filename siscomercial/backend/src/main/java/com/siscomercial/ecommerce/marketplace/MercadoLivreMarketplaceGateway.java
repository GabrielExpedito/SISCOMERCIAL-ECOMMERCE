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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter oficial do Mercado Livre. Nenhum token sai deste processo de backend.
 */
@Component
@RequiredArgsConstructor
public class MercadoLivreMarketplaceGateway implements MarketplaceGateway {
    private static final URI ITEMS_URI = URI.create("https://api.mercadolibre.com/items");
    private final CredencialMarketplaceService credencialService;
    private final MercadoLivreCategoriaService categoriaService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public Marketplace marketplace() {
        return Marketplace.MERCADO_LIVRE;
    }

    @Override
    public List<ResultadoPedido> buscarPedidos(IntegracaoMarketplace integracao, LocalDateTime alteradosDesde) {
        try {
            String token = credencialService.revelar(integracao.getTokenProtegido());
            String sellerId = integracao.getIdentificadorExterno();
            if (sellerId == null || sellerId.isBlank()) {
                throw new RegraNegocioException("A integração do Mercado Livre não possui o identificador da conta " + "vendedora.");
            }

            List<ResultadoPedido> pedidos = new ArrayList<>();
            int offset = 0;
            final int limite = 50;
            do {
                StringBuilder url =
                        new StringBuilder("https://api.mercadolibre.com/orders/search?seller=").append(URLEncoder.encode(sellerId, StandardCharsets.UTF_8)).append("&order.status=paid,cancelled&sort=date_desc&limit=").append(limite).append("&offset=").append(offset);

                LocalDateTime referencia = alteradosDesde != null ? alteradosDesde.minusMinutes(5) :
                        LocalDateTime.now().minusDays(30);
                String desde =
                        OffsetDateTime.of(referencia, ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                url.append("&order.date_last_updated.from=").append(URLEncoder.encode(desde, StandardCharsets.UTF_8));

                JsonNode resposta = executarGet(URI.create(url.toString()), token, "consultar pedidos do Mercado " +
                        "Livre");
                JsonNode resultados = resposta.path("results");
                if (!resultados.isArray() || resultados.isEmpty()) {
                    break;
                }

                for (JsonNode pedido : resultados) {
                    pedidos.add(mapearPedido(pedido));
                }

                int quantidade = resultados.size();
                int total = resposta.path("paging").path("total").asInt(offset + quantidade);
                offset += quantidade;
                if (quantidade < limite || offset >= total) {
                    break;
                }
            } while (true);

            return pedidos;
        } catch (RegraNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new RegraNegocioException("Falha ao consultar pedidos do Mercado Livre: " + e.getMessage());
        }
    }

    private ResultadoPedido mapearPedido(JsonNode pedido) {
        List<ResultadoItemPedido> itens = new ArrayList<>();
        JsonNode itensNode = pedido.path("order_items");
        if (itensNode.isArray()) {
            for (JsonNode item : itensNode) {
                String itemId = item.path("item").path("id").asText("");
                int quantidade = item.path("quantity").asInt(0);
                BigDecimal valor = item.hasNonNull("unit_price") ? item.path("unit_price").decimalValue() :
                        BigDecimal.ZERO;
                if (!itemId.isBlank() && quantidade > 0) {
                    itens.add(new ResultadoItemPedido(itemId, quantidade, valor));
                }
            }
        }

        return new ResultadoPedido(pedido.path("id").asText(), pedido.path("status").asText(""),
                dataHoraMercadoLivre(pedido.path("date_created").asText(null)), dataHoraMercadoLivre(pedido.path(
                "date_last_updated").asText(null)), pedido.hasNonNull("total_amount") ? pedido.path(
                "total_amount").decimalValue() : BigDecimal.ZERO,
                pedido.path("currency_id").asText(null), itens);
    }

    private LocalDateTime dataHoraMercadoLivre(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valor).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(valor, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e) {
                return null;
            }
        }
    }

    @Override
    public ResultadoPublicacao publicar(IntegracaoMarketplace integracao, Produto produto) {
        validarProduto(produto);

        try {
            String token = credencialService.revelar(integracao.getTokenProtegido());
            validarAtributosObrigatorios(integracao, produto);
            Map<String, Object> corpo = montarCorpoItem(produto, token);

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

    @Override
    public ResultadoOperacao encerrar(IntegracaoMarketplace integracao, String identificadorExterno) {
        if (identificadorExterno == null || identificadorExterno.isBlank()) {
            throw new RegraNegocioException("A publicacao nao possui identificador externo do Mercado Livre.");
        }

        try {
            String token = credencialService.revelar(integracao.getTokenProtegido());
            URI uri = URI.create(ITEMS_URI + "/" + identificadorExterno);
            Map<String, String> corpo = Map.of("status", "closed");
            JsonNode item = executarPut(uri, token, corpo, "encerrar o anuncio no Mercado Livre");
            String status = item.path("status").asText("closed");
            if (!"closed".equalsIgnoreCase(status)) {
                throw new RegraNegocioException("O Mercado Livre respondeu ao encerramento, mas o anuncio permaneceu " +
                        "com status: " + status);
            }
            return new ResultadoOperacao(status);
        } catch (RegraNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new RegraNegocioException("Falha ao encerrar o anuncio no Mercado Livre: " + e.getMessage());
        }
    }

    @Override
    public ResultadoSincronizacao sincronizar(IntegracaoMarketplace integracao, String identificadorExterno) {
        if (identificadorExterno == null || identificadorExterno.isBlank()) {
            throw new RegraNegocioException("A publicacao nao possui identificador externo do Mercado Livre.");
        }

        try {
            String token = credencialService.revelar(integracao.getTokenProtegido());
            URI uri = URI.create(ITEMS_URI + "/" + identificadorExterno);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode item = response.body() == null || response.body().isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(response.body());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RegraNegocioException(
                        mensagemErroMercadoLivre(response.statusCode(), item, "consultar o anuncio no Mercado Livre")
                );
            }

            String status = item.path("status").asText("").toLowerCase();
            if (status.isBlank()) {
                throw new RegraNegocioException("O Mercado Livre nao retornou o status do anuncio " + identificadorExterno + ".");
            }

            int quantidade = item.path("available_quantity").asInt(0);
            String permalink = item.path("permalink").asText(null);
            return new ResultadoSincronizacao(status, quantidade, permalink);
        } catch (RegraNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new RegraNegocioException("Falha ao sincronizar o anuncio no Mercado Livre: " + e.getMessage());
        }
    }

    private void validarProduto(Produto produto) {
        if (produto.getNome() == null || produto.getNome().isBlank()) {
            throw new RegraNegocioException("O produto deve possuir nome para ser publicado.");
        }
        if (produto.getCategoriaMercadoLivreId() == null || !produto.getCategoriaMercadoLivreId().matches("MLB\\d+")) {
            throw new RegraNegocioException("O produto deve possuir uma categoria valida do Mercado Livre (ex.: " +
                    "MLB123456). Use o preditor de categorias antes de publicar.");
        }
        if (produto.getQuantidadeDisponivel() <= 0) {
            throw new RegraNegocioException("O produto precisa ter estoque disponivel para ser publicado.");
        }
        BigDecimal preco = preco(produto);
        if (preco == null || preco.signum() <= 0) {
            throw new RegraNegocioException("O produto precisa possuir preco de venda valido para ser publicado.");
        }
        if (produto.getImagens() == null || produto.getImagens().stream().noneMatch(this::urlValida)) {
            throw new RegraNegocioException("O produto precisa possuir pelo menos uma imagem publica (URL http/https)" +
                    " para ser publicado no " + "Mercado Livre.");
        }
    }

    private Map<String, Object> montarCorpoItem(Produto produto, String token) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        // Sellers habilitados ao modelo User Products exigem family_name.
        // O título é gerado pelo Mercado Livre a partir da família e dos atributos.
        corpo.put("family_name", produto.getNome());
        corpo.put("category_id", produto.getCategoriaMercadoLivreId());
        corpo.put("price", preco(produto));
        corpo.put("currency_id", "BRL");
        corpo.put("available_quantity", produto.getQuantidadeDisponivel());
        corpo.put("buying_mode", "buy_it_now");
        corpo.put("listing_type_id", "gold_special");
        corpo.put("condition", "new");
        corpo.put("pictures", montarImagens(produto, token));
        corpo.put("attributes", montarAtributos(produto));
        return corpo;
    }

    private void validarAtributosObrigatorios(IntegracaoMarketplace integracao, Produto produto) {
        List<MercadoLivreCategoriaService.AtributoCategoria> atributosCategoria =
                categoriaService.listarAtributos(integracao, produto.getCategoriaMercadoLivreId());

        for (MercadoLivreCategoriaService.AtributoCategoria atributo : atributosCategoria) {
            JsonNode tags = atributo.tags();
            boolean obrigatorio =
                    tags != null && (tags.path("required").asBoolean(false) || tags.path("new_required").asBoolean(false));
            if (!obrigatorio) {
                continue;
            }

            ProdutoAtributoEncontrado encontrado = encontrarAtributo(produto, atributo.id());
            if (encontrado == null || (vazio(encontrado.valueId()) && vazio(encontrado.valueName()))) {
                throw new RegraNegocioException("Preencha o atributo obrigatorio do Mercado Livre: " + atributo.name() + ".");
            }
        }
    }

    private ProdutoAtributoEncontrado encontrarAtributo(Produto produto, String atributoId) {
        if (produto.getAtributosMercadoLivre() == null) {
            return null;
        }
        return produto.getAtributosMercadoLivre().stream().filter(item -> atributoId.equals(item.getAtributoId())).map(item -> new ProdutoAtributoEncontrado(item.getValueId(), item.getValueName())).findFirst().orElse(null);
    }

    private List<Map<String, Object>> montarAtributos(Produto produto) {
        List<Map<String, Object>> atributos = new ArrayList<>();
        if (produto.getAtributosMercadoLivre() == null) {
            return atributos;
        }

        produto.getAtributosMercadoLivre().stream().filter(item -> !vazio(item.getAtributoId())).filter(item -> !vazio(item.getValueId()) || !vazio(item.getValueName())).forEach(item -> {
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

    private record ProdutoAtributoEncontrado(String valueId, String valueName) {
    }

    /**
     * Envia as imagens para o armazenamento do Mercado Livre antes de criar o item.
     * <p>
     * O Mercado Livre aceita URLs públicas em pictures.source, mas o download da imagem
     * é feito pelos servidores do próprio Mercado Livre. Para não depender de localhost,
     * ngrok, DNS ou permissões do servidor de origem, fazemos o upload direto para o ML
     * e utilizamos a secure_url retornada por ele no payload da publicação.
     */
    private List<Map<String, String>> montarImagens(Produto produto, String token) throws Exception {
        List<Map<String, String>> imagens = new ArrayList<>();
        List<String> urls = new ArrayList<>();

        if (urlValida(produto.getImagemPrincipal())) {
            urls.add(produto.getImagemPrincipal());
        }
        if (produto.getImagens() != null) {
            produto.getImagens().stream().filter(this::urlValida).filter(url -> !urls.contains(url)).forEach(urls::add);
        }

        for (String url : urls.stream().limit(6).toList()) {
            String secureUrl = enviarImagemAoMercadoLivre(url, token);
            Map<String, String> imagem = new LinkedHashMap<>();
            imagem.put("source", secureUrl);
            imagens.add(imagem);
        }

        if (imagens.isEmpty()) {
            throw new RegraNegocioException("Nenhuma imagem valida foi enviada ao Mercado Livre para a publicacao.");
        }

        return imagens;
    }

    private String enviarImagemAoMercadoLivre(String urlImagem, String token) throws Exception {
        HttpRequest downloadRequest =
                HttpRequest.newBuilder(URI.create(urlImagem)).timeout(Duration.ofSeconds(30)).header("Accept", "image" +
                        "/jpeg,image/png,image/*").GET().build();

        HttpResponse<byte[]> downloadResponse = httpClient.send(downloadRequest,
                HttpResponse.BodyHandlers.ofByteArray());

        if (downloadResponse.statusCode() < 200 || downloadResponse.statusCode() >= 300) {
            throw new RegraNegocioException("Nao foi possivel baixar a imagem do produto antes do envio ao Mercado " +
                    "Livre. " + "HTTP " + downloadResponse.statusCode() + ": " + urlImagem);
        }

        byte[] conteudo = downloadResponse.body();
        if (conteudo == null || conteudo.length == 0) {
            throw new RegraNegocioException("A imagem do produto esta vazia: " + urlImagem);
        }

        String contentType =
                downloadResponse.headers().firstValue("Content-Type").map(valor -> valor.split(";", 2)[0].trim().toLowerCase()).orElse("image/jpeg");

        if (!contentType.equals("image/jpeg") && !contentType.equals("image/png")) {
            throw new RegraNegocioException("O Mercado Livre aceita JPG/JPEG/PNG para esta integracao. " + "Content" +
                    "-Type recebido: " + contentType);
        }

        String extensao = contentType.equals("image/png") ? "png" : "jpg";
        String nomeArquivo = obterNomeArquivo(urlImagem, extensao);
        String boundary = "----SiscomercialMercadoLivre" + System.nanoTime();

        byte[] corpoMultipart = montarMultipartImagem(boundary, nomeArquivo, contentType, conteudo);

        URI uri = URI.create("https://api.mercadolibre.com/pictures/items/upload");
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).header("Authorization",
                "Bearer " + token).header("Accept", "application/json").header("Content-Type", "multipart/form-data; " +
                "boundary=" + boundary).POST(HttpRequest.BodyPublishers.ofByteArray(corpoMultipart)).build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        JsonNode json = response.body() == null || response.body().isBlank() ? objectMapper.createObjectNode() :
                objectMapper.readTree(response.body());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RegraNegocioException(mensagemErroMercadoLivre(response.statusCode(), json, "enviar a imagem do" +
                    " produto ao Mercado Livre"));
        }

        String secureUrl = localizarSecureUrl(json);
        if (secureUrl == null || secureUrl.isBlank()) {
            throw new RegraNegocioException("O Mercado Livre recebeu a imagem, mas nao retornou uma URL segura para a" +
                    " publicacao.");
        }

        return secureUrl;
    }

    private byte[] montarMultipartImagem(String boundary, String nomeArquivo, String contentType, byte[] conteudo) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String inicio =
                "--" + boundary + "\r\n" + "Content-Disposition: form-data; name=\"file\"; filename=\"" + nomeArquivo + "\"\r\n" + "Content-Type: " + contentType + "\r\n\r\n";

        out.write(inicio.getBytes(StandardCharsets.UTF_8));
        out.write(conteudo);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private String obterNomeArquivo(String urlImagem, String extensao) {
        try {
            String caminho = URI.create(urlImagem).getPath();
            String nome = caminho == null ? "" : caminho.substring(caminho.lastIndexOf('/') + 1);
            nome = nome.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (nome.isBlank()) {
                nome = "produto." + extensao;
            } else if (!nome.toLowerCase().matches(".*\\.(jpg|jpeg|png)$")) {
                nome = nome + "." + extensao;
            }
            return nome;
        } catch (Exception e) {
            return "produto." + extensao;
        }
    }

    private String localizarSecureUrl(JsonNode json) {
        JsonNode variations = json.path("variations");
        if (!variations.isArray()) {
            return null;
        }

        String fallback = null;
        for (JsonNode variation : variations) {
            String secureUrl = variation.path("secure_url").asText("");
            if (!secureUrl.isBlank()) {
                if (variation.path("size").asText("").equals("1920x1920")) {
                    return secureUrl;
                }
                if (fallback == null) {
                    fallback = secureUrl;
                }
            }
        }
        return fallback;
    }

    private void publicarDescricao(String itemId, String descricao, String token) throws Exception {
        if (descricao == null || descricao.isBlank()) {
            return;
        }

        Map<String, String> corpo = Map.of("plain_text", descricao);
        URI uri = URI.create(ITEMS_URI + "/" + itemId + "/description");
        executarPost(uri, token, corpo, "enviar a descricao do produto ao Mercado Livre");
    }

    private JsonNode executarGet(URI uri, String token, String operacao) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("Authorization",
                "Bearer " + token).header("Accept", "application/json").GET().build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode json = response.body() == null || response.body().isBlank() ? objectMapper.createObjectNode() :
                objectMapper.readTree(response.body());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RegraNegocioException(mensagemErroMercadoLivre(response.statusCode(), json, operacao));
        }
        return json;
    }

    private JsonNode executarPost(URI uri, String token, Object corpo, String operacao) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("Authorization",
                "Bearer " + token).header("Content-Type", "application/json").header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(corpo))).build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = response.body() == null || response.body().isBlank() ? objectMapper.createObjectNode() :
                objectMapper.readTree(response.body());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RegraNegocioException(mensagemErroMercadoLivre(response.statusCode(), json, operacao));
        }
        return json;
    }

    private JsonNode executarPut(URI uri, String token, Object corpo, String operacao) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(corpo)))
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
