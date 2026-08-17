package com.siscomercial.ecommerce.ai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;

import java.util.List;

/**
 * Implementacao "vazia" usada quando nenhuma chave de API de LLM foi
 * configurada (siscomercial.ia.openai-api-key). Permite que o backend suba e
 * o endpoint do agente responda de forma clara em vez de falhar, para que o
 * restante do sistema (catalogo, checkout, retaguarda) possa ser testado
 * mesmo sem uma chave de LLM configurada ainda.
 *
 * Assim que uma chave real for configurada (ex: OPENAI_API_KEY), o
 * AgenteIAConfig passa a usar o OpenAiChatModel de verdade automaticamente.
 */
public class StubChatLanguageModel implements ChatLanguageModel {

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        String aviso = "O agente de IA ainda nao esta configurado com uma chave de LLM " +
                "(defina a variavel de ambiente OPENAI_API_KEY ou a propriedade " +
                "siscomercial.ia.openai-api-key). As ferramentas do agente (consultarPedido, " +
                "consultarProduto, cancelarPedido, emitirNotaFiscal, etc.) ja estao implementadas " +
                "e prontas para uso assim que a chave for configurada.";
        return Response.from(AiMessage.from(aviso));
    }
}
