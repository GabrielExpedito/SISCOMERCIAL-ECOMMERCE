package com.siscomercial.ecommerce.controller;

import com.siscomercial.ecommerce.ai.AgenteAssistantFactory;
import com.siscomercial.ecommerce.ai.AgenteIAAssistant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Endpoint publico (por enquanto) do agente de IA administrativo (RF009).
 * Em producao, este endpoint deve ficar atras de autenticacao/autorizacao
 * de administrador (item "Usuarios: autenticacao, autorizacao e permissoes"
 * do escopo, secao 2) - ainda nao implementado neste prototipo.
 */
@RestController
@RequestMapping("/api/ia")
@RequiredArgsConstructor
public class AgenteChatController {

    private final AgenteAssistantFactory assistantFactory;

    public record MensagemRequest(String sessionId, String mensagem) {}
    public record MensagemResponse(String sessionId, String resposta) {}

    @PostMapping("/chat")
    public MensagemResponse chat(@RequestBody MensagemRequest request) {
        String sessionId = (request.sessionId() == null || request.sessionId().isBlank())
                ? UUID.randomUUID().toString()
                : request.sessionId();

        AgenteIAAssistant assistant = assistantFactory.paraSessao(sessionId);
        String resposta = assistant.chat(request.mensagem());
        return new MensagemResponse(sessionId, resposta);
    }

    @GetMapping("/exemplo-comando")
    public Map<String, String> exemploComando() {
        // Exemplo da secao 1.1 / 9.3 da especificacao
        return Map.of(
                "comando", "Emita a nota fiscal do pedido SIS-2026-000001 e envie para mim pelo WhatsApp.",
                "observacao", "O agente vai pedir confirmacao antes de emitir a nota e antes de enviar pelo WhatsApp."
        );
    }
}
