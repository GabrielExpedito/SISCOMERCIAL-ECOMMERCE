package com.siscomercial.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siscomercial.ecommerce.config.CustomOAuth2UserService;
import com.siscomercial.ecommerce.config.SecurityConfig;
import com.siscomercial.ecommerce.exception.RegraNegocioException;
import com.siscomercial.ecommerce.model.*;
import com.siscomercial.ecommerce.model.DTO.RetaguardaDTOs.AlterarStatusAdminRequest;
import com.siscomercial.ecommerce.model.DTO.RetaguardaDTOs.CancelarPedidoAdminRequest;
import com.siscomercial.ecommerce.service.PedidoService;
import com.siscomercial.ecommerce.service.NotaFiscalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RetaguardaPedidoController.class)
@Import(SecurityConfig.class)
class RetaguardaPedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PedidoService pedidoService;

    @MockBean
    private NotaFiscalService notaFiscalService;

    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;

    private Pedido pedidoExemplo;

    @BeforeEach
    void setUp() {
        Cliente cliente = new Cliente();
        cliente.setId(10L);
        cliente.setNomeRazaoSocial("Comprador Teste");
        cliente.setEmail("comprador@teste.com");
        cliente.setCpfCnpj("12345678900");
        cliente.setTelefone("11999999999");

        pedidoExemplo = new Pedido();
        pedidoExemplo.setId(1L);
        pedidoExemplo.setNumeroPedido("SIS-2026-000001");
        pedidoExemplo.setDataHora(LocalDateTime.now());
        pedidoExemplo.setCliente(cliente);
        pedidoExemplo.setStatus(StatusPedido.PAGAMENTO_APROVADO);
        pedidoExemplo.setSubtotal(new BigDecimal("150.00"));
        pedidoExemplo.setFrete(new BigDecimal("20.00"));
        pedidoExemplo.setTotal(new BigDecimal("170.00"));
        pedidoExemplo.setEnderecoCep("01001-000");
        pedidoExemplo.setEnderecoLogradouro("Praça da Sé");
        pedidoExemplo.setEnderecoNumero("100");
        pedidoExemplo.setEnderecoCidade("São Paulo");
        pedidoExemplo.setEnderecoEstado("SP");
        pedidoExemplo.setItens(new ArrayList<>());
    }

    @Test
    void deveBloquearAcessoNaoAutenticado() throws Exception {
        mockMvc.perform(get("/api/retaguarda/pedidos"))
                .andExpect(status().isFound()); // OAuth2 redireciona para /oauth2/authorization/google
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void deveBloquearAcessoComRoleCliente() throws Exception {
        mockMvc.perform(get("/api/retaguarda/pedidos"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin@siscomercial.com")
    void devePermitirListagemPaginadaParaAdmin() throws Exception {
        when(pedidoService.listarPedidosRetaguarda(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pedidoExemplo)));

        mockMvc.perform(get("/api/retaguarda/pedidos")
                        .param("status", "PAGAMENTO_APROVADO")
                        .param("numeroPedido", "SIS-2026-000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].numeroPedido").value("SIS-2026-000001"))
                .andExpect(jsonPath("$.content[0].clienteNome").value("Comprador Teste"))
                .andExpect(jsonPath("$.content[0].total").value(170.00));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deveRetornarDetalheCompletoDoPedido() throws Exception {
        when(pedidoService.buscarPorId(1L)).thenReturn(pedidoExemplo);

        mockMvc.perform(get("/api/retaguarda/pedidos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.numeroPedido").value("SIS-2026-000001"))
                .andExpect(jsonPath("$.cliente.email").value("comprador@teste.com"))
                .andExpect(jsonPath("$.enderecoEntrega.cidade").value("São Paulo"));
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "operador@siscomercial.com")
    void deveAvancarStatusComSucesso() throws Exception {
        pedidoExemplo.setStatus(StatusPedido.EM_SEPARACAO);
        when(pedidoService.alterarStatus(eq(1L), eq(StatusPedido.EM_SEPARACAO), eq(OrigemAlteracaoStatusPedido.ADMINISTRADOR), any()))
                .thenReturn(pedidoExemplo);

        AlterarStatusAdminRequest request = new AlterarStatusAdminRequest(StatusPedido.EM_SEPARACAO);

        mockMvc.perform(post("/api/retaguarda/pedidos/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_SEPARACAO"));

        verify(pedidoService).alterarStatus(eq(1L), eq(StatusPedido.EM_SEPARACAO), eq(OrigemAlteracaoStatusPedido.ADMINISTRADOR), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deveRetornarErro400ParaTransicaoInvalida() throws Exception {
        when(pedidoService.alterarStatus(eq(1L), eq(StatusPedido.ENTREGUE), eq(OrigemAlteracaoStatusPedido.ADMINISTRADOR), any()))
                .thenThrow(new RegraNegocioException("Transicao invalida para o pedido SIS-2026-000001: PAGAMENTO_APROVADO -> ENTREGUE."));

        AlterarStatusAdminRequest request = new AlterarStatusAdminRequest(StatusPedido.ENTREGUE);

        mockMvc.perform(post("/api/retaguarda/pedidos/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Transicao invalida para o pedido SIS-2026-000001: PAGAMENTO_APROVADO -> ENTREGUE."));
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "gerente@siscomercial.com")
    void deveCancelarPedidoComSucesso() throws Exception {
        pedidoExemplo.setStatus(StatusPedido.CANCELADO);
        when(pedidoService.cancelar(eq(1L), eq("Cliente desistiu da compra"), eq(OrigemAlteracaoStatusPedido.ADMINISTRADOR), any()))
                .thenReturn(pedidoExemplo);

        CancelarPedidoAdminRequest request = new CancelarPedidoAdminRequest("Cliente desistiu da compra");

        mockMvc.perform(post("/api/retaguarda/pedidos/1/cancelar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        verify(pedidoService).cancelar(eq(1L), eq("Cliente desistiu da compra"), eq(OrigemAlteracaoStatusPedido.ADMINISTRADOR), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deveFaturarPedidoEmSeparacaoComDadosInformados() throws Exception {
        pedidoExemplo.setStatus(StatusPedido.EM_SEPARACAO);
        when(pedidoService.buscarPorId(1L)).thenReturn(pedidoExemplo);

        mockMvc.perform(post("/api/retaguarda/pedidos/1/faturar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroNota\":\"123\",\"chaveNota\":\"chave-123\"}"))
                .andExpect(status().isOk());

        verify(pedidoService).registrarFaturamento(eq(1L), eq("123"), eq("chave-123"),
                eq(OrigemAlteracaoStatusPedido.ADMINISTRADOR), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "expedicao@siscomercial.com")
    void deveRegistrarEnvioComRastreamento() throws Exception {
        pedidoExemplo.setStatus(StatusPedido.ENVIADO);
        when(pedidoService.registrarEnvio(eq(1L), eq("BR123"), eq(OrigemAlteracaoStatusPedido.ADMINISTRADOR), any()))
                .thenReturn(pedidoExemplo);

        mockMvc.perform(post("/api/retaguarda/pedidos/1/enviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoRastreamento\":\"BR123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENVIADO"));

        verify(pedidoService).registrarEnvio(eq(1L), eq("BR123"), eq(OrigemAlteracaoStatusPedido.ADMINISTRADOR), any());
    }
}
