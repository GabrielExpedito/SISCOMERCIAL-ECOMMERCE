package com.siscomercial.ecommerce.repository;

import com.siscomercial.ecommerce.model.HistoricoIntegracaoMarketplace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HistoricoIntegracaoMarketplaceRepository extends JpaRepository<HistoricoIntegracaoMarketplace, Long> {
    List<HistoricoIntegracaoMarketplace> findByIntegracaoIdOrderByDataHoraDesc(Long integracaoId);
}
