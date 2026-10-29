package br.com.grupopapa.cfp.domain;

/**
 * Etapas do pedido de compra (ver FLUXO PEDIDOS):
 * RASCUNHO -> (envio) -> DIVERGENCIA ou EM_ANALISE -> APROVADO / REPROVADO.
 */
public enum StatusPedidoCompra {
    /** Fornecedor ainda esta digitando. */
    RASCUNHO,
    /** Enviado, mas algum item diverge do cadastro: o fiscal precisa reanalisar o cadastro. */
    DIVERGENCIA,
    /** Fiscal analisando o comparativo com os ultimos precos. */
    EM_ANALISE,
    /** Devolvido ao fornecedor para reavaliacao. */
    REPROVADO,
    /** Aprovado pelo fiscal; gera a sugestao para a rotina 220. */
    APROVADO,
    /** Cancelado antes da aprovacao. */
    CANCELADO;

    public boolean isEditavelPeloFornecedor() {
        return this == RASCUNHO || this == REPROVADO;
    }

    public boolean isEmAnalise() {
        return this == DIVERGENCIA || this == EM_ANALISE;
    }
}
