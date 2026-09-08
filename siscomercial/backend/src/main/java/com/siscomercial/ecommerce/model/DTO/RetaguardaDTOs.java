package com.siscomercial.ecommerce.model.DTO;

import com.siscomercial.ecommerce.model.FormaPagamento;
import com.siscomercial.ecommerce.model.OrigemAlteracaoStatusPedido;
import com.siscomercial.ecommerce.model.StatusPagamento;
import com.siscomercial.ecommerce.model.StatusPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class RetaguardaDTOs {

    public record RetaguardaPedidoResumoDTO(
            Long id,
            String numeroPedido,
            LocalDateTime dataHora,
            Long clienteId,
            String clienteNome,
            String clienteEmail,
            StatusPedido status,
            BigDecimal total,
            int quantidadeItens,
            String modalidadeFrete
    ) {}

    public record RetaguardaPedidoDetalheDTO(
            Long id,
            String numeroPedido,
            LocalDateTime dataHora,
            StatusPedido status,
            BigDecimal subtotal,
            BigDecimal desconto,
            BigDecimal frete,
            BigDecimal total,
            EnderecoDTO enderecoEntrega,
            String modalidadeFrete,
            Integer prazoEstimadoDias,
            String codigoRastreamento,
            LocalDateTime reservaExpiraEm,
            String numeroNotaFiscal,
            String chaveNotaFiscal,
            ClienteResumoDTO cliente,
            List<ItemPedidoDTO> itens,
            PagamentoDTO pagamento
    ) {}

    public record EnderecoDTO(
            String cep,
            String logradouro,
            String numero,
            String complemento,
            String bairro,
            String cidade,
            String estado
    ) {}

    public record ClienteResumoDTO(
            Long id,
            String nomeRazaoSocial,
            String email,
            String cpfCnpj,
            String telefone
    ) {}

    public record ItemPedidoDTO(
            Long id,
            Long produtoId,
            String produtoCodigo,
            String produtoNome,
            String descricaoMomentoCompra,
            Integer quantidade,
            BigDecimal valorUnitario,
            BigDecimal desconto,
            BigDecimal subtotal
    ) {}

    public record PagamentoDTO(
            Long id,
            FormaPagamento formaPagamento,
            BigDecimal valor,
            String identificadorTransacao,
            StatusPagamento status,
            LocalDateTime dataHoraTentativa,
            LocalDateTime dataHoraConfirmacao
    ) {}

    public record HistoricoStatusDTO(
            Long id,
            StatusPedido statusAnterior,
            StatusPedido novoStatus,
            LocalDateTime dataHora,
            OrigemAlteracaoStatusPedido origem,
            String responsavel
    ) {}

    public record AlterarStatusAdminRequest(
            StatusPedido status
    ) {}

    public record CancelarPedidoAdminRequest(
            String motivo
    ) {}
}