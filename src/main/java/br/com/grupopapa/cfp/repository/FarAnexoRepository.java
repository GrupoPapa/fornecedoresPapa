package br.com.grupopapa.cfp.repository;

import br.com.grupopapa.cfp.domain.FarAnexo;
import br.com.grupopapa.cfp.domain.TipoAnexoFar;
import br.com.grupopapa.cfp.dto.FarAnexoResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FarAnexoRepository extends JpaRepository<FarAnexo, Long> {

    /** Somente os metadados - nao carrega o BLOB. */
    @Query("""
           select new br.com.grupopapa.cfp.dto.FarAnexoResponse(
                  a.id, a.tipo, a.nomeArquivo, a.tipoConteudo, a.tamanho, a.dataCriacao)
             from FarAnexo a
            where a.farId = :farId
            order by a.id
           """)
    List<FarAnexoResponse> listar(@Param("farId") Long farId);

    Optional<FarAnexo> findByIdAndFarId(Long id, Long farId);

    long countByFarId(Long farId);

    long countByFarIdAndTipo(Long farId, TipoAnexoFar tipo);

    /** [farId, quantidade] dos anexos do tipo informado, por FAR. */
    @Query("""
           select a.farId, count(a) from FarAnexo a
            where a.farId in :ids and a.tipo = :tipo
            group by a.farId
           """)
    List<Object[]> contarPorTipo(@Param("ids") Collection<Long> ids, @Param("tipo") TipoAnexoFar tipo);
}
