package com.siscomercial.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configura a disponibilização pública dos arquivos enviados ao sistema.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${siscomercial.upload.diretorio:uploads}")
    private String diretorioUpload;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path diretorio = Paths.get(diretorioUpload)
                .toAbsolutePath()
                .normalize();

        String localizacao = diretorio.toUri().toString();

        if (!localizacao.endsWith("/")) {
            localizacao += "/";
        }

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(localizacao);
    }
}
