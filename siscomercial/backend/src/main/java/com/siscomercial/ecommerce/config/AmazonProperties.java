package com.siscomercial.ecommerce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuração SP-API externa ao código; segredos não são incluídos em toString. */
@Component
@ConfigurationProperties(prefix = "siscomercial.amazon")
public class AmazonProperties {
    private String clientId = "";
    private String clientSecret = "";
    private String refreshToken = "";
    private String sellerId = "";
    private String marketplaceId = "";
    private String baseUrl = "https://sellingpartnerapi-na.amazon.com";
    private String lwaTokenUrl = "https://api.amazon.com/auth/o2/token";
    private String userAgent = "SisComercial/0.1.0 (Language=Java/17)";

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId == null ? "" : clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret == null ? "" : clientSecret; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken == null ? "" : refreshToken; }
    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId == null ? "" : sellerId; }
    public String getMarketplaceId() { return marketplaceId; }
    public void setMarketplaceId(String marketplaceId) { this.marketplaceId = marketplaceId == null ? "" : marketplaceId; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl; }
    public String getLwaTokenUrl() { return lwaTokenUrl; }
    public void setLwaTokenUrl(String lwaTokenUrl) { this.lwaTokenUrl = lwaTokenUrl == null ? "" : lwaTokenUrl; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent == null ? "" : userAgent; }

    public boolean hasLwaCredentials() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !refreshToken.isBlank();
    }

    @Override
    public String toString() {
        return "AmazonProperties{configured=" + hasLwaCredentials() + ", baseUrl='" + baseUrl + "'}";
    }
}
