package com.siscomercial.ecommerce.repository;

import com.siscomercial.ecommerce.model.PublicacaoMarketplace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
import java.util.List;

public interface PublicacaoMarketplaceRepository extends JpaRepository<PublicacaoMarketplace, Long> {
    Optional<PublicacaoMarketplace> findByProdutoIdAndIntegracaoId(Long produtoId, Long integracaoId);

    @EntityGraph(attributePaths = {"produto", "integracao"})
    List<PublicacaoMarketplace> findByIntegracaoIdOrderByUltimaSincronizacaoDesc(Long integracaoId);

    @EntityGraph(attributePaths = {"produto", "integracao"})
    List<PublicacaoMarketplace> findAllByOrderByUltimaSincronizacaoDesc();
}
