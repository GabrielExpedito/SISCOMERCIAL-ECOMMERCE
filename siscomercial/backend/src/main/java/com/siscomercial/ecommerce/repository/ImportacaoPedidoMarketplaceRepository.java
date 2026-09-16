package com.siscomercial.ecommerce.repository;

import com.siscomercial.ecommerce.model.ImportacaoPedidoMarketplace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImportacaoPedidoMarketplaceRepository extends JpaRepository<ImportacaoPedidoMarketplace, Long> {
    Optional<ImportacaoPedidoMarketplace> findByIntegracaoIdAndIdentificadorExterno(Long integracaoId, String identificadorExterno);
}
