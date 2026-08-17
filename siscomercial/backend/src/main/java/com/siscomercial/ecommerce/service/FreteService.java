package com.siscomercial.ecommerce.service;

import com.siscomercial.ecommerce.exception.RegraNegocioException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

/**
 * RF002 - 6.6 Calculo de frete.
 *
 * IMPORTANTE: o provedor real de frete ainda esta "a definir" (secao 11 da
 * especificacao). Esta classe e um STUB isolado propositalmente atras de uma
 * interface simples, para que a integracao real (Correios, Melhor Envio, etc.)
 * seja plugada aqui sem alterar quem a consome (carrinho/checkout/agente).
 */
@Service
public class FreteService {

    public record CotacaoFrete(String modalidade, int prazoDias, BigDecimal valor) {}

    public CotacaoFrete calcular(String cep) {
        // RN014 - CEP obrigatorio
        if (cep == null || !cep.matches("\\d{5}-?\\d{3}")) {
            // RN015 - CEP invalido: informar erro sem consultar servico
            throw new RegraNegocioException("RN015: CEP invalido.");
        }

        // --- STUB --------------------------------------------------------
        // Sem integracao real definida ainda. RN016 exige que, em caso de
        // falha, o sistema informe indisponibilidade em vez de valor ficticio.
        // Aqui devolvemos uma cotacao simulada apenas para permitir testar o
        // fluxo de ponta a ponta (catalogo -> carrinho -> checkout -> pedido).
        return new CotacaoFrete("PAC (simulado)", 7, new BigDecimal("24.90"));
        // -------------------------------------------------------------------
    }
}
