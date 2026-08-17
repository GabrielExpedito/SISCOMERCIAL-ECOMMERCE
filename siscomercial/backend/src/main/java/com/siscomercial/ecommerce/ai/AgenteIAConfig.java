package com.siscomercial.ecommerce.ai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AgenteIAConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Value("${siscomercial.ia.openai-api-key:}") String apiKey,
            @Value("${siscomercial.ia.modelo:gpt-4o-mini}") String modelo) {

        if (apiKey == null || apiKey.isBlank()) {
            // Sem chave configurada ainda: mantem o backend funcional (ver StubChatLanguageModel).
            return new StubChatLanguageModel();
        }

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelo)
                .temperature(0.2)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}
