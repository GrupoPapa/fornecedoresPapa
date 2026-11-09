package br.com.grupopapa.cfp.importacao;

import java.util.List;

/** Planilha com erros: nada foi importado. A lista vai para a tela, linha a linha. */
public class ImportacaoException extends RuntimeException {

    private final transient List<ErroImportacao> erros;

    public ImportacaoException(String mensagem, List<ErroImportacao> erros) {
        super(mensagem);
        this.erros = List.copyOf(erros);
    }

    public List<ErroImportacao> getErros() { return erros; }
}
