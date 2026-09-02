package br.com.grupopapa.cfp.repository;

import br.com.grupopapa.cfp.domain.ProdutoHistorico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProdutoHistoricoRepository extends JpaRepository<ProdutoHistorico, Long> {

    @Query("""
           select h from ProdutoHistorico h
           left join fetch h.usuario
           where h.produto.id = :produtoId
           order by h.dataEvento desc, h.id desc
           """)
    List<ProdutoHistorico> listarPorProduto(@Param("produtoId") Long produtoId);
}
