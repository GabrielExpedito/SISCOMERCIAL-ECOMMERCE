package com.siscomercial.ecommerce.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Fails startup when datasource connection settings are missing or blank. */
@Component
public class DatabaseCredentialsValidator implements InitializingBean {
    private final String url;
    private final String username;
    private final String password;

    public DatabaseCredentialsValidator(
            @Value("${spring.datasource.url:}") String url,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public void afterPropertiesSet() {
        validate(url, username, password);
    }

    static void validate(String url, String username, String password) {
        if (isBlank(url) || isBlank(username) || isBlank(password)) {
            throw new IllegalStateException(
                    "Database configuration is incomplete: DB_URL, DB_USERNAME and DB_PASSWORD are required.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank()
                || (value.contains("${") && value.contains("}"));
    }
}
