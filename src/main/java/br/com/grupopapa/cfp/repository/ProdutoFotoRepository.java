package br.com.grupopapa.cfp.repository;

import br.com.grupopapa.cfp.domain.ProdutoFoto;
import br.com.grupopapa.cfp.dto.FotoResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProdutoFotoRepository extends JpaRepository<ProdutoFoto, Long> {

    /** Lista somente os metadados - nao carrega o BLOB. */
    @Query("""
           select new br.com.grupopapa.cfp.dto.FotoResponse(
                  f.id, f.nomeArquivo, f.tipoConteudo, f.tamanho, f.principal, f.dataCriacao)
             from ProdutoFoto f
            where f.produto.id = :produtoId
            order by f.principal desc, f.id
           """)
    List<FotoResponse> listarPorProduto(@Param("produtoId") Long produtoId);

    Optional<ProdutoFoto> findByIdAndProdutoId(Long id, Long produtoId);

    List<ProdutoFoto> findByProdutoId(Long produtoId);

    long countByProdutoId(Long produtoId);

    void deleteByProdutoId(Long produtoId);
}
