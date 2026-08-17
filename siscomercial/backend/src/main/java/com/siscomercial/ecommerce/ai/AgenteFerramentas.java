package com.siscomercial.ecommerce.ai;

import com.siscomercial.ecommerce.exception.RecursoNaoEncontradoException;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.Pedido;
import com.siscomercial.ecommerce.model.Produto;
import com.siscomercial.ecommerce.service.NotaFiscalService;
import com.siscomercial.ecommerce.service.PedidoService;
import com.siscomercial.ecommerce.service.ProdutoService;
import com.siscomercial.ecommerce.service.WhatsAppService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Ferramentas (Tools) do agente de IA - secao 9.1 da especificacao.
 *
 * PRINCIPIO CENTRAL (secao 3 e 3.1 da especificacao):
 *  - O agente NAO acessa o banco de dados diretamente.
 *  - Toda ferramenta aqui apenas CHAMA os servicos de negocio do backend
 *    (ProdutoService, PedidoService, NotaFiscalService, WhatsAppService),
 *    que sao os MESMOS servicos usados pelo REST API do site.
 *  - Consultas executam direto. Alteracoes de dados e operacoes fiscais
 *    passam pelo OperacaoConfirmacaoService e so rodam apos confirmacao
 *    explicita do administrador (RN de seguranca 9.4).
 */
@Component
@RequiredArgsConstructor
public class AgenteFerramentas {

    private final ProdutoService produtoService;
    private final PedidoService pedidoService;
    private final NotaFiscalService notaFiscalService;
    private final WhatsAppService whatsAppService;
    private final OperacaoConfirmacaoService confirmacaoService;

    // ---------------------------------------------------------------
    // CONSULTAS - podem ser executadas diretamente (secao 3.1 / 9.4)
    // ---------------------------------------------------------------

    @Tool("Consulta os dados completos de um pedido pelo numero (formato SIS-AAAA-NNNNNN). " +
          "Retorna status, itens, valores e dados de entrega.")
    public String consultarPedido(String numeroPedido) {
        try {
            Pedido pedido = pedidoService.buscarPorNumero(numeroPedido);
            return formatarPedido(pedido);
        } catch (RecursoNaoEncontradoException e) {
            return "Nao encontrei nenhum pedido com o numero " + numeroPedido + ".";
        }
    }

    @Tool("Consulta os dados de um produto pelo codigo interno, incluindo preco e status.")
    public String consultarProduto(String codigoInterno) {
        try {
            Produto p = produtoService.buscarPorCodigo(codigoInterno);
            return "Produto %s (%s): status=%s, preco=R$%.2f%s, estoque disponivel=%d".formatted(
                    p.getNome(), p.getCodigoInterno(), p.getStatus(), p.getPrecoVenda(),
                    p.isEmPromocao() ? (" (promocional: R$" + p.getPrecoPromocional() + ")") : "",
                    p.getQuantidadeDisponivel());
        } catch (RecursoNaoEncontradoException e) {
            return "Nao encontrei nenhum produto com o codigo " + codigoInterno + ".";
        }
    }

    @Tool("Consulta a quantidade em estoque disponivel de um produto pelo codigo interno.")
    public String consultarEstoque(String codigoInterno) {
        try {
            Produto p = produtoService.buscarPorCodigo(codigoInterno);
            return "O produto %s tem %d unidade(s) disponivel(is) (estoque total: %d, reservado: %d).".formatted(
                    p.getCodigoInterno(), p.getQuantidadeDisponivel(), p.getQuantidadeEstoque(), p.getQuantidadeReservada());
        } catch (RecursoNaoEncontradoException e) {
            return "Nao encontrei nenhum produto com o codigo " + codigoInterno + ".";
        }
    }

    @Tool("Consulta o status atual de uma nota fiscal pela chave de acesso.")
    public String consultarStatusNotaFiscal(String chaveAcesso) {
        String status = notaFiscalService.consultarStatus(chaveAcesso);
        return "Status da nota fiscal " + chaveAcesso + ": " + status;
    }

    // ---------------------------------------------------------------
    // ALTERACOES - exigem confirmacao explicita do administrador
    // ---------------------------------------------------------------

    @Tool("Solicita o CANCELAMENTO de um pedido pelo numero. Esta operacao ALTERA DADOS e por isso " +
          "NAO E EXECUTADA IMEDIATAMENTE: retorna um token de confirmacao. Antes de chamar " +
          "confirmarOperacao, pergunte ao administrador se ele realmente confirma o cancelamento, " +
          "informando o numero do pedido e o motivo.")
    public String cancelarPedido(String numeroPedido, String motivo) {
        try {
            Pedido pedido = pedidoService.buscarPorNumero(numeroPedido);
            String resumo = "Cancelar o pedido " + numeroPedido + " (motivo: " + motivo + ")";
            String token = confirmacaoService.registrarPendencia(resumo, () -> {
                Pedido cancelado = pedidoService.cancelar(pedido.getId(), motivo);
                return "Pedido " + cancelado.getNumeroPedido() + " cancelado com sucesso. Status atual: " + cancelado.getStatus() + ".";
            });
            return "Encontrei o pedido " + numeroPedido + " (status atual: " + pedido.getStatus() + "). " +
                    "Para confirmar o cancelamento, peca a confirmacao explicita do administrador e depois chame " +
                    "confirmarOperacao com o token: " + token;
        } catch (RecursoNaoEncontradoException e) {
            return "Nao encontrei nenhum pedido com o numero " + numeroPedido + ". Nada foi alterado.";
        } catch (RegraNegocioException e) {
            return "Nao foi possivel preparar o cancelamento: " + e.getMessage();
        }
    }

    @Tool("Solicita a EMISSAO DA NOTA FISCAL de um pedido pelo numero. Operacao FISCAL/FINANCEIRA: " +
          "exige validacoes adicionais e NAO E EXECUTADA IMEDIATAMENTE - retorna um token de confirmacao. " +
          "Peca confirmacao explicita do administrador antes de chamar confirmarOperacao.")
    public String emitirNotaFiscal(String numeroPedido) {
        try {
            Pedido pedido = pedidoService.buscarPorNumero(numeroPedido);
            notaFiscalService.validarPedidoAptoFaturamento(pedido); // valida antes de gerar o token
            String resumo = "Emitir a nota fiscal do pedido " + numeroPedido;
            String token = confirmacaoService.registrarPendencia(resumo, () -> {
                NotaFiscalService.ResultadoEmissao resultado = notaFiscalService.emitir(pedido);
                pedidoService.registrarFaturamento(pedido.getId(), resultado.numeroNota(), resultado.chaveAcesso());
                return "Nota fiscal emitida para o pedido " + pedido.getNumeroPedido() +
                        ": numero " + resultado.numeroNota() + ", chave " + resultado.chaveAcesso() +
                        ", situacao " + resultado.situacao() + ".";
            });
            return "O pedido " + numeroPedido + " esta apto para faturamento. " +
                    "Para confirmar a emissao da nota fiscal, peca a confirmacao explicita do administrador " +
                    "e depois chame confirmarOperacao com o token: " + token;
        } catch (RecursoNaoEncontradoException e) {
            return "Nao encontrei nenhum pedido com o numero " + numeroPedido + ". Nada foi alterado.";
        } catch (RegraNegocioException e) {
            return "Nao foi possivel preparar a emissao da nota fiscal: " + e.getMessage();
        }
    }

    @Tool("Envia um documento (por exemplo, a nota fiscal de um pedido) pelo WhatsApp para um numero de telefone. " +
          "NAO E EXECUTADO IMEDIATAMENTE - retorna um token de confirmacao. Peca confirmacao explicita do " +
          "administrador antes de chamar confirmarOperacao.")
    public String enviarDocumentoWhatsApp(String numeroDestino, String nomeDocumento, String conteudoOuUrlDocumento) {
        String resumo = "Enviar '" + nomeDocumento + "' pelo WhatsApp para " + numeroDestino;
        String token = confirmacaoService.registrarPendencia(resumo, () -> {
            WhatsAppService.ResultadoEnvio resultado = whatsAppService.enviarDocumento(numeroDestino, nomeDocumento, conteudoOuUrlDocumento);
            return resultado.mensagem();
        });
        return "Pronto para enviar '" + nomeDocumento + "' pelo WhatsApp para " + numeroDestino + ". " +
                "Peca confirmacao explicita do administrador e depois chame confirmarOperacao com o token: " + token;
    }

    // ---------------------------------------------------------------
    // CONFIRMACAO - unico caminho para executar operacoes sensiveis
    // ---------------------------------------------------------------

    @Tool("Confirma e EXECUTA de fato uma operacao pendente (cancelamento de pedido, emissao de nota fiscal " +
          "ou envio de documento pelo WhatsApp), usando o token retornado pela ferramenta correspondente. " +
          "SO CHAME ESTA FERRAMENTA depois que o administrador confirmar explicitamente, em linguagem natural, " +
          "que deseja prosseguir com a operacao descrita.")
    public String confirmarOperacao(String token) {
        return confirmacaoService.confirmar(token);
    }

    @Tool("Cancela/descarta uma operacao pendente (que ainda nao foi confirmada), usando o token recebido.")
    public String cancelarOperacaoPendente(String token) {
        return confirmacaoService.cancelar(token);
    }

    private String formatarPedido(Pedido pedido) {
        StringBuilder sb = new StringBuilder();
        sb.append("Pedido ").append(pedido.getNumeroPedido())
          .append(" - status: ").append(pedido.getStatus())
          .append(", cliente: ").append(pedido.getCliente().getNomeRazaoSocial())
          .append(", total: R$").append(pedido.getTotal())
          .append(", itens: ").append(pedido.getItens().size());
        if (pedido.getNumeroNotaFiscal() != null) {
            sb.append(", nota fiscal: ").append(pedido.getNumeroNotaFiscal());
        }
        if (pedido.getCodigoRastreamento() != null) {
            sb.append(", rastreamento: ").append(pedido.getCodigoRastreamento());
        }
        return sb.toString();
    }
}
