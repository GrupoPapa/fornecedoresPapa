package br.com.grupopapa.cfp.dto;

import jakarta.validation.constraints.Size;

public record AnaliseRequest(
        @Size(max = 500, message = "Parecer deve ter no maximo 500 caracteres") String parecer) {
}
