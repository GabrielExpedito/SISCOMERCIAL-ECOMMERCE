package com.siscomercial.ecommerce;

import com.siscomercial.ecommerce.model.*;
import com.siscomercial.ecommerce.repository.ClienteRepository;
import com.siscomercial.ecommerce.service.PedidoService;
import com.siscomercial.ecommerce.service.ProdutoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.util.List;

@SpringBootApplication
public class SiscomercialApplication {

    public static void main(String[] args) {
        SpringApplication.run(SiscomercialApplication.class, args);
    }
}
