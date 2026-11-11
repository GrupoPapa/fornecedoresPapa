package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.StatusProduto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Todos os produtos do fornecedor: os que ja estao no WinThor (antigos e os novos ja gravados
 * la) e os cadastrados pelo portal que ainda nao tem CODPROD.
 */
public record CatalogoProdutosResponse(List<Item> produtos, int qtdWinthor, int qtdPortal, String aviso) {

    /** origem WINTHOR (tem codProd) ou PORTAL (produto novo, tem produtoId e status do cadastro). */
    public record Item(String origem, Long codProd, Long produtoId, String codigoFabrica, String descricao,
                       String ean, String ncm, String embalagem, String unidade, BigDecimal qtdEmbalagem,
                       BigDecimal pesoLiquido, BigDecimal pesoBruto, LocalDate dataCadastro,
                       boolean foraDeLinha, StatusProduto status, String pedidoNumero, Long pedidoId) { }
}
