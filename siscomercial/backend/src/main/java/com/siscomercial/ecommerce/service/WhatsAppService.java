package com.siscomercial.ecommerce.service;

import org.springframework.stereotype.Service;

/**
 * Secao 8.4 - Integracao com WhatsApp (envio de mensagens/documentos).
 * Provedor ainda "a definir" (secao 11). STUB isolado para plugar a API real
 * (ex: WhatsApp Business Cloud API) sem alterar quem consome este servico.
 */
@Service
public class WhatsAppService {

    public record ResultadoEnvio(boolean sucesso, String mensagem) {}

    public ResultadoEnvio enviarDocumento(String numeroDestino, String nomeDocumento, String urlOuConteudo) {
        // --- STUB: aqui entraria a chamada real ao provedor de WhatsApp ---
        return new ResultadoEnvio(true,
                "Documento '" + nomeDocumento + "' enviado (simulado) para " + numeroDestino);
        // ---------------------------------------------------------------------
    }

    public ResultadoEnvio enviarMensagem(String numeroDestino, String texto) {
        return new ResultadoEnvio(true, "Mensagem enviada (simulada) para " + numeroDestino);
    }
}
