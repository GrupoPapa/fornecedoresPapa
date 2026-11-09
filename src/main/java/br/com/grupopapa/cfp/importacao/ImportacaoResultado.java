package br.com.grupopapa.cfp.importacao;

import java.util.List;

/** Resultado da importacao: o pedido em que os itens entraram. */
public record ImportacaoResultado(Long pedidoId, String pedidoNumero, int importados, List<String> avisos) {
}
