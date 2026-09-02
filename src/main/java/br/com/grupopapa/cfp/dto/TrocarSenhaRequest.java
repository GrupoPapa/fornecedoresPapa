package br.com.grupopapa.cfp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrocarSenhaRequest(
        @NotBlank(message = "Informe a senha atual") String senhaAtual,
        @NotBlank(message = "Informe a nova senha")
        @Size(min = 8, max = 60, message = "A nova senha deve ter no minimo 8 caracteres") String novaSenha) {
}
