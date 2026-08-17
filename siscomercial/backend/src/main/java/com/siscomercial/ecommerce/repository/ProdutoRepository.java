package com.siscomercial.ecommerce.repository;

import com.siscomercial.ecommerce.model.Produto;
import com.siscomercial.ecommerce.model.StatusProduto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    Optional<Produto> findByCodigoInterno(String codigoInterno);
    boolean existsByCodigoInterno(String codigoInterno);
    List<Produto> findByStatus(StatusProduto status);
    List<Produto> findByNomeContainingIgnoreCaseOrCodigoInternoContainingIgnoreCase(String nome, String codigo);
}
