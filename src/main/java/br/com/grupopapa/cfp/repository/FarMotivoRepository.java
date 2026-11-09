package br.com.grupopapa.cfp.repository;

import br.com.grupopapa.cfp.domain.FarMotivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FarMotivoRepository extends JpaRepository<FarMotivo, Long> {

    List<FarMotivo> findAllByOrderByDescricao();

    List<FarMotivo> findByAtivoTrueOrderByDescricao();

    @Query("""
           select case when count(m) > 0 then true else false end from FarMotivo m
           where upper(m.descricao) = upper(:descricao)
             and (:ignorarId is null or m.id <> :ignorarId)
           """)
    boolean existeDescricao(@Param("descricao") String descricao, @Param("ignorarId") Long ignorarId);
}
