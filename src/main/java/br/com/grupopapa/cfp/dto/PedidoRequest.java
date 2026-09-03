package br.com.grupopapa.cfp.dto;

import jakarta.validation.constraints.Size;

/** Dados que o fornecedor pode informar no pedido. */
public record PedidoRequest(
        @Size(max = 500, message = "Observacao deve ter no maximo 500 caracteres") String observacao) {
}
