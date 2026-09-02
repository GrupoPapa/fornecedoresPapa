package br.com.grupopapa.cfp.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param usuario e-mail do usuario ou, para fornecedores, o codigo dele no WinThor.
 */
public record LoginRequest(
        @NotBlank(message = "Informe o e-mail ou o codigo do fornecedor") String usuario,
        @NotBlank(message = "Informe a senha") String senha) {
}
