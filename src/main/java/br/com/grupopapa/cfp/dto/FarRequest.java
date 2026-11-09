package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.StatusFar;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/** Dados da FAR enviados pelo formulario. obsFarEmpresa so e aceita do administrador. */
public record FarRequest(
        @NotNull(message = "Informe a data da visita") LocalDate dataVisita,
        @NotBlank(message = "Informe o codigo do produto")
        @Size(max = 200, message = "No maximo 200 caracteres") String codigoProduto,
        @NotNull(message = "Informe a quantidade de produtos")
        @Min(value = 1, message = "A quantidade deve ser maior que zero")
        @Max(value = 9999999, message = "Quantidade acima do permitido") Integer qtdProdutos,
        @NotNull(message = "Selecione o motivo") Long motivoId,
        @Size(max = 1000, message = "No maximo 1000 caracteres") String obsOcorrencia,
        LocalDate dataAberturaFar,
        @Size(max = 30, message = "No maximo 30 caracteres") String numeroFar,
        @Size(max = 30, message = "No maximo 30 caracteres") String numeroNota,
        @Size(max = 30, message = "No maximo 30 caracteres") String numeroPedido,
        @NotNull(message = "Selecione a situacao") StatusFar status,
        @Size(max = 100, message = "No maximo 100 caracteres") String statusOutros,
        @Size(max = 1000, message = "No maximo 1000 caracteres") String obsFarFornecedor,
        @Size(max = 1000, message = "No maximo 1000 caracteres") String obsFarEmpresa) {
}
