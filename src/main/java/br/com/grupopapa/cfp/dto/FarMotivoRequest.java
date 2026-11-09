package br.com.grupopapa.cfp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FarMotivoRequest(
        @NotBlank(message = "Informe a descricao do motivo")
        @Size(max = 100, message = "No maximo 100 caracteres") String descricao,
        Boolean ativo) {
}
