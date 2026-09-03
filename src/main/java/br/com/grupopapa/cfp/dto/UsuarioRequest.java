package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.Perfil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioRequest(
        @NotBlank(message = "Informe o nome") @Size(max = 150) String nome,
        @NotBlank(message = "Informe o e-mail") @Email(message = "E-mail invalido") @Size(max = 150) String email,
        @NotNull(message = "Informe o perfil") Perfil perfil,
        Long fornecedorId,
        @Size(min = 8, max = 60, message = "A senha deve ter no minimo 8 caracteres") String senhaProvisoria,
        Boolean ativo,
        /** Envia os dados de acesso para o e-mail informado (padrao: true). */
        Boolean enviarEmail) {
}
