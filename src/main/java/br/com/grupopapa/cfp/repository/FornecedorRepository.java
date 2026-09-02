package br.com.grupopapa.cfp.repository;

import br.com.grupopapa.cfp.domain.Fornecedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FornecedorRepository extends JpaRepository<Fornecedor, Long> {

    Optional<Fornecedor> findByCnpj(String cnpj);

    boolean existsByCnpj(String cnpj);

    Optional<Fornecedor> findByCodFornecedorErp(Long codFornecedorErp);

    @Query("""
           select f from Fornecedor f
           where (:termo is null
                  or upper(f.razaoSocial) like upper(concat('%', :termo, '%'))
                  or upper(coalesce(f.nomeFantasia, '')) like upper(concat('%', :termo, '%'))
                  or f.cnpj like concat('%', :termo, '%'))
           order by f.razaoSocial
           """)
    Page<Fornecedor> buscar(@Param("termo") String termo, Pageable pageable);
}
