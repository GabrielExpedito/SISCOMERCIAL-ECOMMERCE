package com.siscomercial.ecommerce.controller;

import com.siscomercial.ecommerce.config.CustomOAuth2UserService;
import com.siscomercial.ecommerce.config.SecurityConfig;
import com.siscomercial.ecommerce.model.Cliente;
import com.siscomercial.ecommerce.model.OrigemAlteracaoStatusPedido;
import com.siscomercial.ecommerce.model.Pedido;
import com.siscomercial.ecommerce.model.PerfilUsuario;
import com.siscomercial.ecommerce.model.StatusPedido;
import com.siscomercial.ecommerce.service.ClienteService;
import com.siscomercial.ecommerce.service.PedidoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.mock.web.MockHttpSession;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PedidoController.class)
@Import(SecurityConfig.class)
class PedidoSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PedidoService pedidoService;

    @MockBean
    private ClienteService clienteService;

    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void requisicaoDeAlteracaoSemTokenCsrfERejeitada() throws Exception {
        mockMvc.perform(post("/api/pedidos/1/status")
                        .contentType("application/json")
                        .content("{\"status\":\"EM_SEPARACAO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutInvalidaSessao() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        assertThrows(IllegalStateException.class, session::getAttributeNames);
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void clienteNaoPodeConfirmarPagamentoArbitrariamente() throws Exception {
        mockMvc.perform(post("/api/pedidos/1/confirmar-pagamento"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requisicaoAnonimaNaoPodeConfirmarPagamento() throws Exception {
        mockMvc.perform(post("/api/pedidos/1/confirmar-pagamento"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void clienteRecebe403AoAlterarStatusManualmente() throws Exception {
        mockMvc.perform(post("/api/pedidos/1/status")
                        .contentType("application/json")
                        .content("{\"status\":\"EM_SEPARACAO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requisicaoAnonimaNaoPodeAlterarStatusManualmente() throws Exception {
        mockMvc.perform(post("/api/pedidos/1/status")
                        .contentType("application/json")
                        .content("{\"status\":\"EM_SEPARACAO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPodeAlterarStatusManualValido() throws Exception {
        Cliente admin = new Cliente();
        admin.setEmail("admin@example.test");
        admin.setPerfil(PerfilUsuario.ADMIN);
        Pedido pedido = new Pedido();
        pedido.setStatus(StatusPedido.EM_SEPARACAO);
        org.mockito.Mockito.when(clienteService.buscarAutenticado(org.mockito.ArgumentMatchers.any()))
                .thenReturn(admin);
        org.mockito.Mockito.when(pedidoService.alterarStatus(1L, StatusPedido.EM_SEPARACAO,
                        OrigemAlteracaoStatusPedido.ADMINISTRADOR, "admin@example.test"))
                .thenReturn(pedido);

        mockMvc.perform(post("/api/pedidos/1/status")
                        .with(oidcLogin().idToken(token -> token.subject("admin-sub").claim("email", "admin@example.test"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"EM_SEPARACAO\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value("EM_SEPARACAO"));
    }
}
