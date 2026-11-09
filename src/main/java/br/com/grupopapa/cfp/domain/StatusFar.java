package br.com.grupopapa.cfp.domain;

/** Situacoes da FAR, as mesmas da planilha de controle de avarias. */
public enum StatusFar {
    EM_ANALISE("Em analise"),
    PENDENTE_INFORMACOES("Pendente de informacoes"),
    AGUARDANDO_DESCARTE("Aguardando descarte"),
    /** Exige o comprovante de pagamento anexado. */
    FINALIZADO_APROVADO("Processo finalizado aprovado"),
    FINALIZADO_REPROVADO("Processo finalizado reprovado"),
    /** Exige a descricao da situacao (STATUS_OUTROS). */
    OUTROS("Outros");

    private final String rotulo;

    StatusFar(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() { return rotulo; }

    public boolean isFinalizado() {
        return this == FINALIZADO_APROVADO || this == FINALIZADO_REPROVADO;
    }
}
