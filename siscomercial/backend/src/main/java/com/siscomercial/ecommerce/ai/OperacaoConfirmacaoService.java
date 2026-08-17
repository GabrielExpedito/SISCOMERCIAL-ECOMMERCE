package com.siscomercial.ecommerce.ai;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Guarda em memoria as operacoes do agente de IA que estao aguardando
 * confirmacao do administrador (RN de seguranca da secao 9.4). Nenhuma
 * operacao sensivel roda antes de o administrador confirmar explicitamente.
 *
 * Para producao com multiplas instancias, trocar o ConcurrentHashMap por
 * um armazenamento compartilhado (ex: tabela no Postgres/Supabase ou Redis).
 */
@Service
public class OperacaoConfirmacaoService {

    private static final int MINUTOS_VALIDADE_TOKEN = 10;

    private final Map<String, ConfirmacaoPendente> pendencias = new ConcurrentHashMap<>();

    public String registrarPendencia(String resumoOperacao, Supplier<String> executor) {
        String token = UUID.randomUUID().toString().substring(0, 8);
        pendencias.put(token, new ConfirmacaoPendente(token, resumoOperacao, executor, MINUTOS_VALIDADE_TOKEN));
        return token;
    }

    public String confirmar(String token) {
        ConfirmacaoPendente pendencia = pendencias.remove(token);
        if (pendencia == null) {
            return "Nao encontrei nenhuma operacao pendente com o token '" + token + "'. Ela pode ja ter sido executada, expirado ou o token esta incorreto.";
        }
        if (pendencia.isExpirada()) {
            return "O prazo para confirmar esta operacao expirou. Peca ao administrador para solicitar novamente.";
        }
        return pendencia.executar();
    }

    public String cancelar(String token) {
        ConfirmacaoPendente removida = pendencias.remove(token);
        return removida != null
                ? "Operacao cancelada: " + removida.getResumoOperacao()
                : "Nao havia nenhuma operacao pendente com esse token.";
    }
}
