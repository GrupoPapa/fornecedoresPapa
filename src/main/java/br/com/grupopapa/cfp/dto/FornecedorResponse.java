package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.Fornecedor;
import br.com.grupopapa.cfp.domain.TipoFornecedor;

public record FornecedorResponse(
        Long id, String cnpj, String razaoSocial, String nomeFantasia,
        String email, String telefone, Long codFornecedorErp,
        TipoFornecedor tipo, Boolean ativo) {

    public static FornecedorResponse de(Fornecedor f) {
        return new FornecedorResponse(f.getId(), f.getCnpj(), f.getRazaoSocial(), f.getNomeFantasia(),
                f.getEmail(), f.getTelefone(), f.getCodFornecedorErp(), f.getTipo(), f.getAtivo());
    }
}
