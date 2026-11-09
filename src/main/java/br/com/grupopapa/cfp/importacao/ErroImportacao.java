package br.com.grupopapa.cfp.importacao;

/** Problema encontrado em uma linha da planilha (linha = numero da linha no Excel; 0 = cabecalho). */
public record ErroImportacao(int linha, String coluna, String mensagem) {
}
