package com.siscomercial.ecommerce.ai;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cria (e reutiliza) uma instancia de AgenteIAAssistant por sessao de
 * conversa, cada uma com sua propria memoria de curto prazo (ChatMemory),
 * para que o administrador possa manter uma conversa com contexto - por
 * exemplo, confirmar uma operacao pendente na mensagem seguinte.
 */
@Component
@RequiredArgsConstructor
public class AgenteAssistantFactory {

    private static final int JANELA_MEMORIA_MENSAGENS = 20;

    private final ChatLanguageModel chatLanguageModel;
    private final AgenteFerramentas ferramentas;

    private final Map<String, AgenteIAAssistant> assistentesPorSessao = new ConcurrentHashMap<>();

    public AgenteIAAssistant paraSessao(String sessionId) {
        return assistentesPorSessao.computeIfAbsent(sessionId, id ->
                AiServices.builder(AgenteIAAssistant.class)
                        .chatLanguageModel(chatLanguageModel)
                        .chatMemory(MessageWindowChatMemory.withMaxMessages(JANELA_MEMORIA_MENSAGENS))
                        .tools(ferramentas)
                        .build());
    }
}
