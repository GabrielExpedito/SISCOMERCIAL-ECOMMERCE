package com.siscomercial.ecommerce.service;

import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.*;
import com.siscomercial.ecommerce.repository.HistoricoStatusPedidoRepository;
import com.siscomercial.ecommerce.repository.PedidoRepository;
import com.siscomercial.ecommerce.repository.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceStatusTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private HistoricoStatusPedidoRepository historicoStatusPedidoRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private ProdutoService produtoService;
    @Mock private EstoqueService estoqueService;
    @Mock private FreteService freteService;
    @Mock private NumeroPedidoService numeroPedidoService;

    @InjectMocks private PedidoService pedidoService;

    @Test
    void deveRegistrarHistoricoAoAlterarStatusPermitido() {
        Pedido pedido = pedidoNoStatus(StatusPedido.PAGAMENTO_APROVADO);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Pedido resultado = pedidoService.alterarStatus(1L, StatusPedido.EM_SEPARACAO,
                OrigemAlteracaoStatusPedido.ADMINISTRADOR, "admin@siscomercial.com");

        assertEquals(StatusPedido.EM_SEPARACAO, resultado.getStatus());
        ArgumentCaptor<HistoricoStatusPedido> captor = ArgumentCaptor.forClass(HistoricoStatusPedido.class);
        verify(historicoStatusPedidoRepository).save(captor.capture());
        HistoricoStatusPedido historico = captor.getValue();
        assertEquals(StatusPedido.PAGAMENTO_APROVADO, historico.getStatusAnterior());
        assertEquals(StatusPedido.EM_SEPARACAO, historico.getNovoStatus());
        assertEquals(OrigemAlteracaoStatusPedido.ADMINISTRADOR, historico.getOrigem());
        assertEquals("admin@siscomercial.com", historico.getResponsavel());
    }

    @Test
    void deveRejeitarTransicaoInvalidaSemPersistirHistorico() {
        Pedido pedido = pedidoNoStatus(StatusPedido.AGUARDANDO_PAGAMENTO);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));

        assertThrows(RegraNegocioException.class, () -> pedidoService.alterarStatus(1L, StatusPedido.FATURADO,
                OrigemAlteracaoStatusPedido.ADMINISTRADOR, "admin@siscomercial.com"));

        verifyNoInteractions(historicoStatusPedidoRepository);
        verify(pedidoRepository, never()).save(any());
    }

    private Pedido pedidoNoStatus(StatusPedido status) {
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setNumeroPedido("SIS-2026-000001");
        pedido.setStatus(status);
        return pedido;
    }
}
