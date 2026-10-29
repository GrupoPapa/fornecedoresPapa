package br.com.grupopapa.cfp.domain;

/** PCSUGESTAOCOMPRAC.TIPODESCARGA: 1 = normal, 5 = bonificado. */
public enum TipoPedidoCompra {
    NORMAL("1"),
    BONIFICADO("5");

    private final String tipoDescarga;

    TipoPedidoCompra(String tipoDescarga) {
        this.tipoDescarga = tipoDescarga;
    }

    public String getTipoDescarga() {
        return tipoDescarga;
    }
}
