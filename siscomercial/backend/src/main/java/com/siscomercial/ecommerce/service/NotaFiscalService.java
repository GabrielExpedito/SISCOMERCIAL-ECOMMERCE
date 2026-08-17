package com.siscomercial.ecommerce.service;

import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.Pedido;
import com.siscomercial.ecommerce.model.StatusPedido;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * RF005 / secao 8.3 - Faturamento e emissao de NF.
 *
 * O provedor/API fiscal ainda esta "a definir" (secao 11). Esta classe
 * concentra a REGRA de negocio de quando um pedido esta apto a faturamento
 * (isso permanece no backend mesmo quando chamado pelo agente de IA - secao 3),
 * e isola a chamada externa real (SEFAZ / provedor de NF-e) atras de um STUB.
 */
@Service
public class NotaFiscalService {

    public record ResultadoEmissao(String numeroNota, String chaveAcesso, String situacao) {}

    public void validarPedidoAptoFaturamento(Pedido pedido) {
        if (pedido.getStatus() != StatusPedido.PAGAMENTO_APROVADO
                && pedido.getStatus() != StatusPedido.EM_SEPARACAO) {
            throw new RegraNegocioException(
                    "Pedido " + pedido.getNumeroPedido() + " nao esta apto para faturamento (status atual: "
                            + pedido.getStatus() + "). E necessario pagamento aprovado.");
        }
        if (pedido.getNumeroNotaFiscal() != null) {
            throw new RegraNegocioException("Pedido " + pedido.getNumeroPedido() + " ja possui nota fiscal emitida.");
        }
    }

    /** Emite a NF. STUB: sem provedor fiscal definido ainda, gera um numero simulado. */
    public ResultadoEmissao emitir(Pedido pedido) {
        validarPedidoAptoFaturamento(pedido);

        // --- STUB: substituir pela chamada ao provedor de NF-e definido ---
        String numero = "NFe-" + LocalDateTime.now().getYear() + "-" + String.format("%06d", pedido.getId());
        String chave = UUID.randomUUID().toString().replace("-", "");
        return new ResultadoEmissao(numero, chave, "AUTORIZADA (simulada)");
        // --------------------------------------------------------------------
    }

    public String consultarStatus(String chaveAcesso) {
        // --- STUB ---
        return "AUTORIZADA (simulada)";
    }
}
