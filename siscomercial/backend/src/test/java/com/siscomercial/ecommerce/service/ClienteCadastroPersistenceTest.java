package com.siscomercial.ecommerce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siscomercial.ecommerce.config.PasswordConfig;
import com.siscomercial.ecommerce.controller.ClienteController;
import com.siscomercial.ecommerce.model.Cliente;
import com.siscomercial.ecommerce.model.PerfilUsuario;
import com.siscomercial.ecommerce.repository.ClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({ClienteService.class, PasswordConfig.class})
class ClienteCadastroPersistenceTest {
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ClienteService clienteService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ClienteController(clienteService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()))
                .build();
    }

    @Test
    void cadastroValidoPersisteClienteComPerfilPadrao() throws Exception {
        mockMvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":{\"nomeRazaoSocial\":\"Cliente Persistido\","
                                + "\"cpfCnpj\":\"12345678901\",\"email\":\"persistido@example.test\","
                                + "\"telefone\":\"11999990000\"},\"senha\":\"senha-ficticia\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("CLIENTE"));

        Cliente persistido = clienteRepository.findByEmail("persistido@example.test").orElseThrow();
        assertNotNull(persistido.getId());
        assertEquals(PerfilUsuario.CLIENTE, persistido.getPerfil());
        assertNotEquals("senha-ficticia", persistido.getSenhaHash());
    }

    @Test
    void camposPrivilegiadosEIdManipuladoNaoAlteramOuSobrescrevemClienteExistente() throws Exception {
        Cliente existente = new Cliente();
        existente.setNomeRazaoSocial("Conta Administrativa");
        existente.setEmail("admin@example.test");
        existente.setPerfil(PerfilUsuario.ADMIN);
        existente = clienteRepository.saveAndFlush(existente);
        Long idExistente = existente.getId();

        mockMvc.perform(post("/api/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perfil\":\"ADMIN\",\"cliente\":{\"id\":" + idExistente
                                + ",\"nomeRazaoSocial\":\"Novo Cliente\",\"cpfCnpj\":\"98765432100\","
                                + "\"email\":\"novo@example.test\",\"telefone\":\"11988880000\","
                                + "\"perfil\":\"ADMIN\",\"role\":\"ADMIN\","
                                + "\"authorities\":[\"ROLE_ADMIN\"],\"permissoes\":[\"ADMIN\"],"
                                + "\"status\":\"BLOQUEADO\",\"googleId\":\"injetado\","
                                + "\"senhaHash\":\"hash-injetado\"},\"senha\":\"senha-ficticia\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("CLIENTE"))
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(idExistente.intValue())));

        Cliente novo = clienteRepository.findByEmail("novo@example.test").orElseThrow();
        Cliente adminPreservado = clienteRepository.findById(idExistente).orElseThrow();
        assertNotEquals(idExistente, novo.getId());
        assertEquals(PerfilUsuario.CLIENTE, novo.getPerfil());
        assertEquals("ATIVO", novo.getStatus());
        assertEquals(PerfilUsuario.ADMIN, adminPreservado.getPerfil());
        assertEquals("Conta Administrativa", adminPreservado.getNomeRazaoSocial());
    }
}
