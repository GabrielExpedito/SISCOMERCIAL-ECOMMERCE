package com.siscomercial.ecommerce.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Year;

/**
 * Responsavel pela geracao do numero publico do pedido.
 */
@Service
@RequiredArgsConstructor
public class NumeroPedidoService {

    private final JdbcTemplate jdbcTemplate;

    public String gerar() {

        Long sequencia = jdbcTemplate.queryForObject(
                "SELECT nextval('pedido_numero_seq')",
                Long.class
        );

        if (sequencia == null) {
            throw new IllegalStateException(
                    "Nao foi possivel gerar o numero do pedido."
            );
        }

        return String.format(
                "SIS-%d-%06d",
                Year.now().getValue(),
                sequencia
        );
    }
}