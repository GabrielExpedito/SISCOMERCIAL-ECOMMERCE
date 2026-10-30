package com.siscomercial.ecommerce.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Encaminha as páginas institucionais para a aplicação React.
 */
@Controller
public class LegalPageController {

    @GetMapping({"/termos-de-uso", "/politica-de-privacidade"})
    public String exibirPaginaLegal() {
        return "forward:/index.html";
    }
}
