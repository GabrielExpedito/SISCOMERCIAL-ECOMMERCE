package com.siscomercial.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {

    @Value("${siscomercial.upload.diretorio:uploads}")
    private String diretorioUpload;

    @Override
    public void addResourceHandlers(
            ResourceHandlerRegistry registry
    ) {

        Path diretorioProdutos =
                Paths.get(diretorioUpload)
                        .resolve("produtos")
                        .toAbsolutePath()
                        .normalize();

        String localizacao =
                diretorioProdutos
                        .toUri()
                        .toString();

        if (!localizacao.endsWith("/")) {
            localizacao += "/";
        }

        registry.addResourceHandler(
                "/uploads/produtos/**"
        ).addResourceLocations(
                localizacao
        );
    }
}