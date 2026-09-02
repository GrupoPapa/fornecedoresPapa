package br.com.grupopapa.cfp.domain;

/**
 * Define quais medidas o fornecedor informa no cadastro de produtos.
 *
 * GERAL             -> quantidade por embalagem, peso por unidade e cubagem em m3.
 * PISO_REVESTIMENTO -> m2 por caixa, peso por m2 e m2 por pallet
 *                      (no WinThor, produtos com CODEPTO 13).
 */
public enum TipoFornecedor {
    GERAL,
    PISO_REVESTIMENTO;

    public boolean isPiso() {
        return this == PISO_REVESTIMENTO;
    }
}
