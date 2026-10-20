package com.siscomercial.ecommerce.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siscomercial.ecommerce.config.AmazonProperties;

import java.net.http.HttpResponse;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import java.time.Instant;

/** Formatação de diagnósticos Amazon sem incluir credenciais ou corpos completos. */
final class AmazonDiagnosticSupport {
    private AmazonDiagnosticSupport() {}

    static String httpError(String service, HttpResponse<String> response, ObjectMapper mapper,
                            AmazonProperties properties, String accessToken) {
        int status = response.statusCode();
        JsonNode json = parse(response.body(), mapper);
        String category = service.equals("LWA") ? "LWA_AUTH" : categoriaSpApi(status, json);
        Set<String> errors = new LinkedHashSet<>();
        JsonNode nodes = json.path("errors").isArray() ? json.path("errors") : json.path("issues");
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                String code = node.path("code").asText("").trim();
                String message = node.path("message").asText("").trim();
                String details = node.path("details").asText("").trim();
                String item = code.isBlank() ? message : "[" + code + "] " + message;
                if (!details.isBlank() && !details.equals(message)) item += " Detalhes: " + details;
                if (!item.isBlank()) errors.add(redact(item, properties, accessToken));
            }
        }
        if (errors.isEmpty()) {
            String code = json.path("error").asText(json.path("code").asText(""));
            String message = json.path("error_description").asText(json.path("message").asText(""));
            String item = code.isBlank() ? message : "[" + code + "] " + message;
            if (!item.isBlank()) errors.add(redact(item, properties, accessToken));
        }
        String requestId = requestId(response);
        return service + " " + category + " (HTTP " + status + ")"
                + (errors.isEmpty() ? "." : ": " + String.join("; ", errors))
                + (requestId == null ? "" : " (Request ID: " + requestId + ")");
    }

    static String requestId(HttpResponse<?> response) {
        if (response == null || response.headers() == null) return null;
        return response.headers().firstValue("x-amzn-RequestId")
                .or(() -> response.headers().firstValue("x-amz-request-id"))
                .or(() -> response.headers().firstValue("x-amzn-requestid"))
                .map(String::trim).filter(value -> !value.isBlank()).orElse(null);
    }

    static String redact(String text, AmazonProperties properties, String accessToken) {
        String safe = text == null ? "" : text;
        if (properties != null) {
            safe = replace(safe, properties.getClientSecret());
            safe = replace(safe, properties.getRefreshToken());
            safe = replace(safe, properties.getClientId());
        }
        return replace(safe, accessToken);
    }

    static String tokenDiagnostic(String lwaToken, String sentToken, AmazonProperties properties,
                                  Instant obtainedAt, Instant requestAt) {
        boolean same = java.util.Objects.equals(lwaToken, sentToken);
        String key = properties == null ? "" : properties.getTokenDiagnosticHmacKey();
        String lwaId = fingerprint(lwaToken, key);
        String sentId = fingerprint(sentToken, key);
        return "lwaTokenNull=" + (lwaToken == null)
                + " lwaTokenEmpty=" + (lwaToken == null || lwaToken.isBlank())
                + " lwaTokenLength=" + (lwaToken == null ? 0 : lwaToken.length())
                + " lwaTokenId=" + lwaId
                + " spApiTokenNull=" + (sentToken == null)
                + " spApiTokenEmpty=" + (sentToken == null || sentToken.isBlank())
                + " spApiTokenLength=" + (sentToken == null ? 0 : sentToken.length())
                + " spApiTokenId=" + sentId
                + " tokensEqual=" + same
                + " lwaObtainedAt=" + (obtainedAt == null ? "unavailable" : obtainedAt)
                + " spApiRequestAt=" + (requestAt == null ? "unavailable" : requestAt);
    }

    private static String fingerprint(String token, String key) {
        if (token == null || key == null || key.isBlank()) return "unavailable";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            return "unavailable";
        }
    }

    private static String replace(String text, String secret) {
        return secret == null || secret.isBlank() ? text : text.replace(secret, "[REDACTED]");
    }

    private static JsonNode parse(String body, ObjectMapper mapper) {
        if (body == null || body.isBlank()) return mapper.getNodeFactory().nullNode();
        try { return mapper.readTree(body); } catch (Exception ignored) { return mapper.getNodeFactory().nullNode(); }
    }

    private static String categoriaSpApi(int status, JsonNode json) {
        if (json.path("issues").isArray() && !json.path("issues").isEmpty()) return "SP_API_VALIDATION";
        if (status == 401) return "SP_API_AUTHORIZATION";
        if (status == 403) {
            String code = json.path("errors").path(0).path("code").asText("").toLowerCase();
            String message = json.path("errors").path(0).path("message").asText("").toLowerCase();
            if (code.contains("unauthor") || code.contains("accessdenied") || code.contains("forbidden")
                    || message.contains("access denied") || message.contains("invalid signature")
                    || message.contains("expired token")) return "SP_API_AUTHORIZATION";
            return "SP_API_FORBIDDEN_UNCLASSIFIED";
        }
        if (status == 400 || status == 404 || status == 415 || status == 422) return "SP_API_VALIDATION";
        if (status == 429 || status >= 500) return "SP_API_AVAILABILITY";
        return "SP_API_HTTP";
    }
}
