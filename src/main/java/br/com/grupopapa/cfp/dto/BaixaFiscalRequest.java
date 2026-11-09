package br.com.grupopapa.cfp.dto;

import jakarta.validation.constraints.Size;

/** Vinculo da FAR com o processo de baixa fiscal da avaria no sistema. */
public record BaixaFiscalRequest(
        @Size(max = 100, message = "No maximo 100 caracteres") String referencia,
        @Size(max = 500, message = "No maximo 500 caracteres") String observacao) {
}
