package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.StatusFar;
import jakarta.validation.constraints.*;

import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;

/** Dados da FAR enviados pelo formulario. obsFarEmpresa so e aceita do administrador. */
public record FarRequest(
        @NotNull(message = "Informe a data da visita") LocalDate dataVisita,
        @NotEmpty(message = "Informe ao menos um produto")
        @Size(max = 100, message = "No maximo 100 produtos por FAR")
        List<@Valid Item> itens,
        @Size(max = 1000, message = "No maximo 1000 caracteres") String obsOcorrencia,
        LocalDate dataAberturaFar,
        @Size(max = 30, message = "No maximo 30 caracteres") String numeroFar,
        @Size(max = 30, message = "No maximo 30 caracteres") String numeroNota,
        @Size(max = 30, message = "No maximo 30 caracteres") String numeroPedido,
        @NotNull(message = "Selecione a situacao") StatusFar status,
        @Size(max = 100, message = "No maximo 100 caracteres") String statusOutros,
        @Size(max = 1000, message = "No maximo 1000 caracteres") String obsFarFornecedor,
        @Size(max = 1000, message = "No maximo 1000 caracteres") String obsFarEmpresa) {

    /** Um produto da FAR. */
    public record Item(
            @NotBlank(message = "Informe o codigo do produto")
            @Size(max = 60, message = "No maximo 60 caracteres") String codigoProduto,
            @Size(max = 200, message = "No maximo 200 caracteres") String descricaoProduto,
            @NotNull(message = "Informe a quantidade")
            @Min(value = 1, message = "A quantidade deve ser maior que zero")
            @Max(value = 9999999, message = "Quantidade acima do permitido") Integer quantidade,
            @NotNull(message = "Selecione o motivo") Long motivoId) {
    }
}
