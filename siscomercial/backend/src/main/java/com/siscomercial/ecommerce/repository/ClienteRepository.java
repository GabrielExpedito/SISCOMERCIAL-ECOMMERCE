package com.siscomercial.ecommerce.repository;

import com.siscomercial.ecommerce.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByEmail(String email);
    Optional<Cliente> findByCpfCnpj(String cpfCnpj);
    boolean existsByEmail(String email);
}
