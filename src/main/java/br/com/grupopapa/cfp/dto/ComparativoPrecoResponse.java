package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.repository.ErpCompraRepository.UltimaCompra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Comparativo de cada item com as ultimas entradas do produto no WinThor. */
public record ComparativoPrecoResponse(BigDecimal alertaPercentual, List<Item> itens) {

    public record Item(Long itemId, Long codProdErp, BigDecimal precoUnitario,
                       BigDecimal ultimoPreco, LocalDate dataUltimaCompra,
                       BigDecimal mediaPreco, BigDecimal variacaoPercentual,
                       String situacao,           // SEM_HISTORICO | ACIMA | ABAIXO | IGUAL | ERRO | PRODUTO_NOVO
                       List<UltimaCompra> ultimas, String erro) { }
}
