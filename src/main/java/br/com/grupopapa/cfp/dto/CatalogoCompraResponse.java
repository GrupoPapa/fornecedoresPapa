package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.StatusProduto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Produtos que o fornecedor pode colocar no pedido.
 * origem WINTHOR = ja cadastrado (tem CODPROD); PORTAL = produto novo, ainda sem CODPROD.
 */
public record CatalogoCompraResponse(List<Produto> produtos, String avisoWinthor,
                                     String codFilialFornecedor) {

    public record Produto(String origem, Long codProdErp, Long produtoId, StatusProduto situacaoCadastro,
                          String codigoFabrica, String descricao, String embalagem, String unidade,
                          BigDecimal qtUnitCx, String codBarras) { }
}
