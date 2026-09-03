package br.com.grupopapa.cfp.repository;

import br.com.grupopapa.cfp.domain.Pedido;
import br.com.grupopapa.cfp.domain.StatusPedido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    Optional<Pedido> findByFornecedorIdAndStatus(Long fornecedorId, StatusPedido status);

    Optional<Pedido> findByNumero(String numero);

    @Query("select coalesce(max(p.sequencia), 0) from Pedido p where p.ano = :ano")
    Integer maiorSequencia(@Param("ano") Integer ano);

    @Query(value = """
           select p from Pedido p
           join fetch p.fornecedor f
           where (:fornecedorId is null or f.id = :fornecedorId)
             and (:status is null or p.status = :status)
             and (:termo is null
                  or upper(p.numero) like upper(concat('%', :termo, '%'))
                  or upper(f.razaoSocial) like upper(concat('%', :termo, '%')))
           """,
           countQuery = """
           select count(p) from Pedido p
           where (:fornecedorId is null or p.fornecedor.id = :fornecedorId)
             and (:status is null or p.status = :status)
             and (:termo is null
                  or upper(p.numero) like upper(concat('%', :termo, '%'))
                  or upper(p.fornecedor.razaoSocial) like upper(concat('%', :termo, '%')))
           """)
    Page<Pedido> pesquisar(@Param("fornecedorId") Long fornecedorId,
                           @Param("status") StatusPedido status,
                           @Param("termo") String termo,
                           Pageable pageable);

    long countByFornecedorIdAndStatus(Long fornecedorId, StatusPedido status);

    long countByStatus(StatusPedido status);
}
