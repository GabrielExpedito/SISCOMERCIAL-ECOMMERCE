package com.siscomercial.ecommerce.repository;

import com.siscomercial.ecommerce.model.PublicacaoMarketplace;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PublicacaoMarketplaceRepository extends JpaRepository<PublicacaoMarketplace, Long> {

    Optional<PublicacaoMarketplace> findByProdutoIdAndIntegracaoId(
            Long produtoId,
            Long integracaoId
    );

    Optional<PublicacaoMarketplace> findByIdentificadorExternoAndIntegracaoId(
            String identificadorExterno,
            Long integracaoId
    );

    @EntityGraph(attributePaths = {"produto", "integracao"})
    List<PublicacaoMarketplace> findByIntegracaoIdOrderByUltimaSincronizacaoDesc(
            Long integracaoId
    );

    @EntityGraph(attributePaths = {"produto", "integracao"})
    List<PublicacaoMarketplace> findAllByOrderByUltimaSincronizacaoDesc();
}