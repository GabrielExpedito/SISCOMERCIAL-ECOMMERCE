package com.siscomercial.ecommerce.repository;

import com.siscomercial.ecommerce.model.IntegracaoMarketplace;
import com.siscomercial.ecommerce.model.Marketplace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IntegracaoMarketplaceRepository extends JpaRepository<IntegracaoMarketplace, Long> {
    Optional<IntegracaoMarketplace> findByMarketplaceAndIdentificadorExterno(Marketplace marketplace, String identificadorExterno);
    Optional<IntegracaoMarketplace> findByOauthState(String oauthState);
}
