package br.com.grupopapa.cfp.repository;

import br.com.grupopapa.cfp.domain.ProdutoErp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProdutoErpRepository extends JpaRepository<ProdutoErp, Long> {

    @Query("select e from ProdutoErp e where e.produto.pedido.id = :pedidoId")
    List<ProdutoErp> listarPorPedido(@Param("pedidoId") Long pedidoId);
}
