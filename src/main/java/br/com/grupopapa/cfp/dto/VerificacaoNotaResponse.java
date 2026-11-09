package br.com.grupopapa.cfp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultado da conferencia da NF de compra no WinThor.
 * consultado = false quando o WinThor nao respondeu (nao bloqueia o fornecedor).
 */
public record VerificacaoNotaResponse(String numero, boolean consultado, boolean encontrada, String mensagem,
                                      LocalDate dataEntrada, String codFilial, Integer qtdItens,
                                      BigDecimal valorTotal, List<Long> produtos) {
}
