package br.com.grupopapa.cfp.dto;

import jakarta.validation.constraints.Size;

/** Senha definida pelo administrador. Em branco = o sistema gera uma automaticamente. */
public record DefinirSenhaRequest(
        @Size(min = 8, max = 60, message = "A senha deve ter no minimo 8 caracteres") String senha,
        /** Envia a nova senha para o e-mail do usuario (padrao: true). */
        Boolean enviarEmail) {
}
