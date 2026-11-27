package br.com.grupopapa.cfp.domain;

/**
 * Etapas do pedido de compra (ver FLUXO PEDIDOS):
 * RASCUNHO -> (envio) -> DIVERGENCIA ou EM_ANALISE -> AGUARDANDO_DIRETORIA -> APROVADO / REPROVADO.
 */
public enum StatusPedidoCompra {
    /** Fornecedor ainda esta digitando. */
    RASCUNHO,
    /** Enviado, mas algum item diverge do cadastro: o fiscal precisa reanalisar o cadastro. */
    DIVERGENCIA,
    /** Fiscal analisando o comparativo com os ultimos precos. */
    EM_ANALISE,
    /** Analisado e enviado para a diretoria aprovar (script 21). */
    AGUARDANDO_DIRETORIA,
    /** Devolvido ao fornecedor para revisao: ele corrige e reenvia. */
    REVISAO,
    /** Reprovado pelo fiscal - encerrado, nao volta para o fornecedor. */
    REPROVADO,
    /** Aprovado pelo fiscal; gera a sugestao para a rotina 220. */
    APROVADO,
    /** Cancelado antes da aprovacao. */
    CANCELADO;

    public boolean isEditavelPeloFornecedor() {
        return this == RASCUNHO || this == REVISAO;
    }

    public boolean isEmAnalise() {
        return this == DIVERGENCIA || this == EM_ANALISE || this == AGUARDANDO_DIRETORIA;
    }
}
