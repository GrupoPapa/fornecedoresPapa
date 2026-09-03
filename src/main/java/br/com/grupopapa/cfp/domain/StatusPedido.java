package br.com.grupopapa.cfp.domain;

public enum StatusPedido {
    /** Fornecedor ainda esta montando o pedido. */
    ABERTO,
    /** Enviado para analise da empresa. */
    ENVIADO,
    /** Analisado e aprovado. */
    APROVADO,
    /** Devolvido para correcao. */
    REPROVADO;

    public boolean isEditavelPeloFornecedor() {
        return this == ABERTO || this == REPROVADO;
    }
}
