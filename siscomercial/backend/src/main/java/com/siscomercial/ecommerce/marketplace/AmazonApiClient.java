package com.siscomercial.ecommerce.marketplace;

import com.siscomercial.ecommerce.config.AmazonProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.format.DateTimeFormatter;

/** Cliente HTTP compartilhado pela autenticação LWA e por futuras operações SP-API. */
@Component
public class AmazonApiClient {
    private static final DateTimeFormatter AMAZON_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private final HttpClient httpClient;

    public AmazonApiClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    AmazonApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public HttpResponse<String> postForm(String url, Map<String, String> fields)
            throws IOException, InterruptedException {
        String body = fields.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpRequest.Builder spApiRequest(AmazonProperties properties, String path, String accessToken) {
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return HttpRequest.newBuilder(URI.create(baseUrl + normalizedPath))
                .timeout(Duration.ofSeconds(20))
                .header("x-amz-access-token", accessToken)
                .header("x-amz-date", AMAZON_DATE.format(Instant.now()))
                .header("user-agent", properties.getUserAgent());
    }

    public HttpResponse<String> sendSpApi(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
