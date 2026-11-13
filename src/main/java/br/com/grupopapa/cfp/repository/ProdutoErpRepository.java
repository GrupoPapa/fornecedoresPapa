package br.com.grupopapa.cfp.repository;

import br.com.grupopapa.cfp.domain.ProdutoErp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProdutoErpRepository extends JpaRepository<ProdutoErp, Long> {

    /** CODPROD do WinThor ja gravado para o produto do portal (vazio se ainda nao foi integrado). */
    @Query("select e.codProdErp from ProdutoErp e where e.produtoId = :produtoId and e.codProdErp is not null")
    List<Long> codProdDoProduto(@Param("produtoId") Long produtoId);

    @Query("select e from ProdutoErp e where e.produto.pedido.id = :pedidoId")
    List<ProdutoErp> listarPorPedido(@Param("pedidoId") Long pedidoId);
}
