package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.TipoFornecedor;

/** Fornecedor lido da tabela PCFORNEC do WinThor (somente leitura). */
public record ErpFornecedorResponse(
        Long codigo,
        String cnpj,
        String razaoSocial,
        String telefone,
        String email,
        TipoFornecedor tipo,
        boolean jaImportado,
        Long fornecedorId) {
}
