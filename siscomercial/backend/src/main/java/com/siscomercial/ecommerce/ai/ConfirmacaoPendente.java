package com.siscomercial.ecommerce.ai;

import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * Representa uma operacao solicitada pelo agente de IA que precisa de
 * confirmacao explicita do administrador antes de ser executada de fato
 * (secao 3.1 e 9.4 da especificacao: "operacoes que alteram dados poderao
 * exigir confirmacao do administrador").
 */
public class ConfirmacaoPendente {

    private final String token;
    private final String resumoOperacao;
    private final Supplier<String> executor;
    private final LocalDateTime criadaEm;
    private final LocalDateTime expiraEm;

    public ConfirmacaoPendente(String token, String resumoOperacao, Supplier<String> executor, int minutosValidade) {
        this.token = token;
        this.resumoOperacao = resumoOperacao;
        this.executor = executor;
        this.criadaEm = LocalDateTime.now();
        this.expiraEm = criadaEm.plusMinutes(minutosValidade);
    }

    public String getToken() { return token; }
    public String getResumoOperacao() { return resumoOperacao; }
    public boolean isExpirada() { return LocalDateTime.now().isAfter(expiraEm); }

    /** Executa a operacao real (chamando os servicos de negocio do backend) e retorna o resultado. */
    public String executar() { return executor.get(); }
}
