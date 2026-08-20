package com.siscomercial.ecommerce.ai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AgenteIAConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Value("${siscomercial.ia.gemini-api-key:}") String apiKey,
            @Value("${siscomercial.ia.modelo:gemini-3.5-flash-lite}") String modelo) {

        System.out.println("=== CONFIGURACAO IA ===");
        System.out.println("Modelo: " + modelo);
        System.out.println("API Key configurada: " +
                (apiKey != null && !apiKey.isBlank()));

        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("Modo: STUB");
            return new StubChatLanguageModel();
        }

        System.out.println("Modo: GEMINI");

        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelo)
                .temperature(0.2)
                .timeout(Duration.ofSeconds(60))
                .logRequestsAndResponses(true)
                .build();
    }
}