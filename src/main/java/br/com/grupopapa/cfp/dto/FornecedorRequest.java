package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.TipoFornecedor;
import br.com.grupopapa.cfp.validation.Cnpj;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FornecedorRequest(
        @NotBlank(message = "Informe o CNPJ") @Cnpj String cnpj,
        @NotBlank(message = "Informe a razao social") @Size(max = 150) String razaoSocial,
        @Size(max = 150) String nomeFantasia,
        @Email(message = "E-mail invalido") @Size(max = 150) String email,
        @Size(max = 20) String telefone,
        Long codFornecedorErp,
        TipoFornecedor tipo,
        Boolean ativo,
        @Size(max = 2, message = "Filial com no maximo 2 caracteres")
        @jakarta.validation.constraints.Pattern(regexp = "\\d{0,2}", message = "Filial invalida") String codFilialCompra) {
}
