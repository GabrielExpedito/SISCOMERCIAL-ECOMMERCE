package com.siscomercial.ecommerce.repository;

import com.siscomercial.ecommerce.model.HistoricoStatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoricoStatusPedidoRepository extends JpaRepository<HistoricoStatusPedido, Long> {
    List<HistoricoStatusPedido> findByPedidoIdOrderByDataHoraAsc(Long pedidoId);
}
