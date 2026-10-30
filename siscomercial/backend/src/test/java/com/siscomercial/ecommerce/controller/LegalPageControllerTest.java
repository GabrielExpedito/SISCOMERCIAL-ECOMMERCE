package com.siscomercial.ecommerce.controller;

import com.siscomercial.ecommerce.config.CustomOAuth2UserService;
import com.siscomercial.ecommerce.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = LegalPageController.class,
        properties = "siscomercial.frontend-url=http://localhost:5173"
)
@Import(SecurityConfig.class)
class LegalPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;

    @Test
    void termosDeUsoDeveSerAcessivelSemAutenticacao() throws Exception {
        mockMvc.perform(get("/termos-de-uso"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void politicaDePrivacidadeDeveSerAcessivelSemAutenticacao() throws Exception {
        mockMvc.perform(get("/politica-de-privacidade"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }
}
