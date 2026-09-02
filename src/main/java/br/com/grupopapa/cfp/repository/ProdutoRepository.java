package br.com.grupopapa.cfp.repository;

import br.com.grupopapa.cfp.domain.Produto;
import br.com.grupopapa.cfp.domain.StatusProduto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    Optional<Produto> findByIdAndFornecedorId(Long id, Long fornecedorId);

    boolean existsByFornecedorIdAndCodigoIgnoreCase(Long fornecedorId, String codigo);

    boolean existsByFornecedorIdAndCodigoIgnoreCaseAndIdNot(Long fornecedorId, String codigo, Long id);

    boolean existsByCodBarraProduto(String codBarraProduto);

    boolean existsByCodBarraProdutoAndIdNot(String codBarraProduto, Long id);

    @Query(value = """
           select p from Produto p
           join fetch p.fornecedor f
           where (:fornecedorId is null or f.id = :fornecedorId)
             and (:status is null or p.status = :status)
             and (:termo is null
                  or upper(p.descricao) like upper(concat('%', :termo, '%'))
                  or upper(p.codigo)    like upper(concat('%', :termo, '%'))
                  or p.codBarraProduto  like concat('%', :termo, '%')
                  or p.codBarraCaixa    like concat('%', :termo, '%')
                  or p.ncm              like concat('%', :termo, '%'))
           """,
           countQuery = """
           select count(p) from Produto p
           where (:fornecedorId is null or p.fornecedor.id = :fornecedorId)
             and (:status is null or p.status = :status)
             and (:termo is null
                  or upper(p.descricao) like upper(concat('%', :termo, '%'))
                  or upper(p.codigo)    like upper(concat('%', :termo, '%'))
                  or p.codBarraProduto  like concat('%', :termo, '%')
                  or p.codBarraCaixa    like concat('%', :termo, '%')
                  or p.ncm              like concat('%', :termo, '%'))
           """)
    Page<Produto> pesquisar(@Param("fornecedorId") Long fornecedorId,
                            @Param("status") StatusProduto status,
                            @Param("termo") String termo,
                            Pageable pageable);

    @Query(value = """
           select p from Produto p
           join fetch p.fornecedor f
           where (:fornecedorId is null or f.id = :fornecedorId)
             and (:status is null or p.status = :status)
           order by f.razaoSocial, p.codigo
           """)
    List<Produto> listarParaExportacao(@Param("fornecedorId") Long fornecedorId,
                                       @Param("status") StatusProduto status);

    long countByFornecedorIdAndStatus(Long fornecedorId, StatusProduto status);

    long countByStatus(StatusProduto status);
}
