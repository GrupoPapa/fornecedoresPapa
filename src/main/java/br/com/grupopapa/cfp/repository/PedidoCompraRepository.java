package br.com.grupopapa.cfp.repository;

import br.com.grupopapa.cfp.domain.PedidoCompra;
import br.com.grupopapa.cfp.domain.StatusPedidoCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PedidoCompraRepository extends JpaRepository<PedidoCompra, Long> {

    @Query("select coalesce(max(p.sequencia), 0) from PedidoCompra p where p.ano = :ano")
    Integer maiorSequencia(@Param("ano") Integer ano);

    @Query("""
           select distinct p from PedidoCompra p
           join fetch p.fornecedor
           left join fetch p.itens i
           left join fetch i.produto
           where p.id = :id
           """)
    Optional<PedidoCompra> buscarComItens(@Param("id") Long id);

    @Query(value = """
           select p from PedidoCompra p
           join fetch p.fornecedor f
           where (:fornecedorId is null or f.id = :fornecedorId)
             and (:status is null or p.status = :status)
             and (:termo is null
                  or upper(p.numero) like upper(concat('%', :termo, '%'))
                  or upper(f.razaoSocial) like upper(concat('%', :termo, '%'))
                  or str(p.numSugestaoErp) = :termo)
           """,
           countQuery = """
           select count(p) from PedidoCompra p
           where (:fornecedorId is null or p.fornecedor.id = :fornecedorId)
             and (:status is null or p.status = :status)
             and (:termo is null
                  or upper(p.numero) like upper(concat('%', :termo, '%'))
                  or upper(p.fornecedor.razaoSocial) like upper(concat('%', :termo, '%'))
                  or str(p.numSugestaoErp) = :termo)
           """)
    Page<PedidoCompra> pesquisar(@Param("fornecedorId") Long fornecedorId,
                                 @Param("status") StatusPedidoCompra status,
                                 @Param("termo") String termo,
                                 Pageable pageable);

    @Query("""
           select distinct p from PedidoCompra p
           join fetch p.fornecedor f
           left join fetch p.itens i
           where (:fornecedorId is null or f.id = :fornecedorId)
             and (:status is null or p.status = :status)
             and (:desde is null or p.dataCriacao >= :desde)
           order by p.id desc
           """)
    List<PedidoCompra> listarParaExportacao(@Param("fornecedorId") Long fornecedorId,
                                            @Param("status") StatusPedidoCompra status,
                                            @Param("desde") OffsetDateTime desde);

    long countByStatus(StatusPedidoCompra status);

    long countByFornecedorIdAndStatus(Long fornecedorId, StatusPedidoCompra status);
}
