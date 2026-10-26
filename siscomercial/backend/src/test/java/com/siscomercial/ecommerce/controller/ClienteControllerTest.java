package com.siscomercial.ecommerce.controller;

import com.siscomercial.ecommerce.config.CustomOAuth2UserService;
import com.siscomercial.ecommerce.config.SecurityConfig;
import com.siscomercial.ecommerce.model.Cliente;
import com.siscomercial.ecommerce.model.PerfilUsuario;
import com.siscomercial.ecommerce.model.DTO.CadastroClienteRequest;
import com.siscomercial.ecommerce.service.ClienteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClienteController.class)
@Import(SecurityConfig.class)
class ClienteControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private ClienteService clienteService;
    @MockBean private CustomOAuth2UserService customOAuth2UserService;

    @Test
    void cadastroLegitimoMantemContratoEUsaDtoDeEntrada() throws Exception {
        when(clienteService.cadastrar(any(CadastroClienteRequest.class)))
                .thenReturn(clienteDeResposta());

        mockMvc.perform(post("/api/clientes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCadastro("")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("CLIENTE"));

        var captor = org.mockito.ArgumentCaptor.forClass(CadastroClienteRequest.class);
        verify(clienteService).cadastrar(captor.capture());
        assertEquals("Maria Cliente", captor.getValue().cliente().nomeRazaoSocial());
        assertEquals("maria@example.test", captor.getValue().cliente().email());
        assertEquals("senha-ficticia", captor.getValue().senha());
    }

    @Test
    void cadastroIgnoraPerfilAdminEIdentificadoresInternosNoJson() throws Exception {
        when(clienteService.cadastrar(any(CadastroClienteRequest.class)))
                .thenReturn(clienteDeResposta());
        String camposPrivilegiados = "\"id\":77,\"perfil\":\"ADMIN\",\"status\":\"BLOQUEADO\","
                + "\"senhaHash\":\"hash-injetado\",\"googleId\":\"google-injetado\"";

        mockMvc.perform(post("/api/clientes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCadastro(camposPrivilegiados)))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(CadastroClienteRequest.class);
        verify(clienteService).cadastrar(captor.capture());
        assertEquals("Maria Cliente", captor.getValue().cliente().nomeRazaoSocial());
    }

    @Test
    void cadastroIgnoraAliasesDePapelEPermissoes() throws Exception {
        when(clienteService.cadastrar(any(CadastroClienteRequest.class)))
                .thenReturn(clienteDeResposta());
        String aliases = "\"role\":\"ADMIN\",\"authorities\":[\"ROLE_ADMIN\"],"
                + "\"permissoes\":[\"ADMINISTRAR_USUARIOS\"]";

        mockMvc.perform(post("/api/clientes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCadastro(aliases)))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(CadastroClienteRequest.class);
        verify(clienteService).cadastrar(captor.capture());
        assertEquals("maria@example.test", captor.getValue().cliente().email());
    }

    @Test
    void cadastroNaoDisponibilizaIdParaSubstituirOutroCliente() throws Exception {
        when(clienteService.cadastrar(any(CadastroClienteRequest.class)))
                .thenReturn(clienteDeResposta());

        mockMvc.perform(post("/api/clientes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCadastro("\"id\":1")))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(CadastroClienteRequest.class);
        verify(clienteService).cadastrar(captor.capture());
        assertEquals("maria@example.test", captor.getValue().cliente().email());
    }

    @Test
    void controllerNaoExpoeEndpointDeAtualizacaoCadastral() {
        boolean possuiAtualizacao = Arrays.stream(ClienteController.class.getDeclaredMethods())
                .anyMatch(method -> method.isAnnotationPresent(PutMapping.class)
                        || method.isAnnotationPresent(PatchMapping.class));

        assertFalse(possuiAtualizacao);
    }

    @Test
    void consultaDeCadastroPassaIdentidadeOidcAoServicoDeAutorizacao() throws Exception {
        when(clienteService.buscarAutorizado(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenReturn(clienteDeResposta());
        mockMvc.perform(get("/api/clientes/1").with(oidcLogin()
                        .idToken(token -> token.subject("cliente-a-sub"))))
                .andExpect(status().isOk());
        verify(clienteService).buscarAutorizado(org.mockito.ArgumentMatchers.eq(1L), any());
    }

    @Test
    void consultaAnonimaDeCadastroContinuaBloqueada() throws Exception {
        mockMvc.perform(get("/api/clientes/1")).andExpect(status().is3xxRedirection());
    }

    @Test
    void administradorPodeConsultarCadastroPeloServicoAutorizado() throws Exception {
        when(clienteService.buscarAutorizado(org.mockito.ArgumentMatchers.eq(2L), any()))
                .thenReturn(clienteDeResposta());
        mockMvc.perform(get("/api/clientes/2").with(oidcLogin()
                        .idToken(token -> token.subject("admin-sub"))
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
        verify(clienteService).buscarAutorizado(org.mockito.ArgumentMatchers.eq(2L), any());
    }

    private String corpoCadastro(String camposExtrasNoCliente) throws Exception {
        String extras = camposExtrasNoCliente.isBlank() ? "" : "," + camposExtrasNoCliente;
        return "{\"cliente\":{\"nomeRazaoSocial\":\"Maria Cliente\","
                + "\"cpfCnpj\":\"12345678901\",\"email\":\"maria@example.test\","
                + "\"telefone\":\"11999990000\",\"nomeFantasia\":\"Maria MEI\","
                + "\"inscricaoEstadual\":\"ISENTO\"" + extras
                + "},\"senha\":\"senha-ficticia\"}";
    }

    private Cliente clienteDeResposta() {
        Cliente cliente = new Cliente();
        cliente.setNomeRazaoSocial("Maria Cliente");
        cliente.setEmail("maria@example.test");
        cliente.setPerfil(PerfilUsuario.CLIENTE);
        return cliente;
    }
}
