package com.siscomercial.ecommerce.ai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AgenteIAConfig {
    private static final Logger log = LoggerFactory.getLogger(AgenteIAConfig.class);

    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Value("${siscomercial.ia.gemini-api-key:}") String apiKey,
            @Value("${siscomercial.ia.modelo:gemini-3.5-flash-lite}") String modelo) {

        if (apiKey == null || apiKey.isBlank()) {
            log.info("AI chat model initialized in stub mode.");
            return new StubChatLanguageModel();
        }

        log.info("AI chat model initialized: provider=Gemini, model={}", modelo);

        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelo)
                .temperature(0.2)
                .timeout(Duration.ofSeconds(60))
                .logRequestsAndResponses(false)
                .build();
    }
}
