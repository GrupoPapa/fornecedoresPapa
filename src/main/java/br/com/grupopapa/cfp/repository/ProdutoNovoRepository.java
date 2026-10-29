package br.com.grupopapa.cfp.repository;

import br.com.grupopapa.cfp.domain.Produto;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Produtos cadastrados pelo fornecedor no portal que ainda nao tem CODPROD no WinThor.
 * Sao os "produtos novos" que podem entrar no pedido de compra, gerando divergencia
 * de cadastro ate o fiscal concluir a gravacao na PCPRODUT.
 */
public interface ProdutoNovoRepository extends Repository<Produto, Long> {

    @Query("""
           select p from Produto p
           where p.fornecedor.id = :fornecedorId
             and not exists (select 1 from ProdutoErp e
                              where e.produtoId = p.id and e.codProdErp is not null)
           order by p.descricao
           """)
    List<Produto> semCodigoWinthor(@Param("fornecedorId") Long fornecedorId);
}
