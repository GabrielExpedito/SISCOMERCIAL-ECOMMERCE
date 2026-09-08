package com.siscomercial.ecommerce.repository;

import com.siscomercial.ecommerce.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository
        extends JpaRepository<Pedido, Long>, JpaSpecificationExecutor<Pedido> {

    Optional<Pedido> findByNumeroPedido(
            String numeroPedido
    );

    List<Pedido> findByClienteIdOrderByDataHoraDesc(
            Long clienteId
    );
}