package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.TipoBaixaFiscal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Vinculo da FAR com o processo de baixa fiscal da avaria no sistema. */
public record BaixaFiscalRequest(
        @NotNull(message = "Informe se a NF e de perda ou de devolucao ao fornecedor") TipoBaixaFiscal tipo,
        @NotBlank(message = "Informe a NF da baixa fiscal")
        @Size(max = 100, message = "No maximo 100 caracteres") String referencia,
        @Size(max = 500, message = "No maximo 500 caracteres") String observacao) {
}
