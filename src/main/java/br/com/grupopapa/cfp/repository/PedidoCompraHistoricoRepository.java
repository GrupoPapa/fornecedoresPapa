package br.com.grupopapa.cfp.repository;

import br.com.grupopapa.cfp.domain.PedidoCompraHistorico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PedidoCompraHistoricoRepository extends JpaRepository<PedidoCompraHistorico, Long> {

    @Query("""
           select h from PedidoCompraHistorico h
           left join fetch h.usuario
           where h.pedidoId = :pedidoId
           order by h.dataEvento desc, h.id desc
           """)
    List<PedidoCompraHistorico> listar(@Param("pedidoId") Long pedidoId);
}
