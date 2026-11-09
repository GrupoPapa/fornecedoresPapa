package br.com.grupopapa.cfp.repository;

import br.com.grupopapa.cfp.domain.FarHistorico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FarHistoricoRepository extends JpaRepository<FarHistorico, Long> {

    @Query("""
           select h from FarHistorico h
           left join fetch h.usuario
           where h.farId = :farId
           order by h.dataEvento desc, h.id desc
           """)
    List<FarHistorico> listar(@Param("farId") Long farId);
}
