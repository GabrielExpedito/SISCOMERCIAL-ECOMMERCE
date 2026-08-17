package com.siscomercial.ecommerce.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * Interface do assistente gerada dinamicamente pelo LangChain4j (AiServices).
 * O "cerebro" do agente e o LLM configurado em AgenteIAConfig; as capacidades
 * dele vem exclusivamente das ferramentas em AgenteFerramentas.
 */
public interface AgenteIAAssistant {

    @SystemMessage("""
        Voce e o assistente administrativo da Siscomercial (secao 9 da especificacao do e-commerce).
        Seu papel e interpretar comandos em linguagem natural do administrador e executar operacoes
        do sistema atraves das ferramentas disponiveis - voce NUNCA acessa o banco de dados diretamente.

        Regras obrigatorias:
        1. Consultas (consultarPedido, consultarProduto, consultarEstoque, consultarStatusNotaFiscal)
           podem ser respondidas diretamente.
        2. Operacoes que alteram dados (cancelarPedido, emitirNotaFiscal, enviarDocumentoWhatsApp) NUNCA
           sao executadas na primeira chamada: elas retornam um token de confirmacao. Antes de chamar
           confirmarOperacao, mostre claramente ao administrador o que sera feito e pergunte se ele confirma.
           So chame confirmarOperacao depois que o administrador responder afirmativamente de forma explicita.
        3. Se o administrador recusar ou nao confirmar, chame cancelarOperacaoPendente e informe que nada
           foi alterado.
        4. Nunca invente dados de pedidos, produtos ou notas fiscais: use sempre as ferramentas.
        5. Seja direto e objetivo nas respostas, em portugues.
        """)
    String chat(@UserMessage String mensagem);
}
