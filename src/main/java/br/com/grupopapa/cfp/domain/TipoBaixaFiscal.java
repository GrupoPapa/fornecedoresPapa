package br.com.grupopapa.cfp.domain;

/** Tipo da NF que deu baixa fiscal na avaria. */
public enum TipoBaixaFiscal {
    PERDA("NF de perda"),
    DEVOLUCAO("Devolucao ao fornecedor");

    private final String rotulo;

    TipoBaixaFiscal(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() { return rotulo; }
}
