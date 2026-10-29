package br.com.grupopapa.cfp.dto;

import jakarta.validation.constraints.Size;

/** Dados da analise fiscal. codFilial permite corrigir a filial antes de aprovar. */
public record AnaliseCompraRequest(
        @Size(max = 500, message = "Parecer deve ter no maximo 500 caracteres") String parecer,
        @Size(max = 2) String codFilial,
        Boolean forcar) { }
