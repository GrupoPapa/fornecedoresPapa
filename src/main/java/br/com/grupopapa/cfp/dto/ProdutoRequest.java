package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.validation.CodigoBarras;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProdutoRequest(
        @NotBlank(message = "Informe o codigo do produto")
        @Size(max = 30, message = "Codigo deve ter no maximo 30 caracteres")
        String codigo,

        @NotBlank(message = "Informe a descricao do produto")
        @Size(max = 150, message = "Descricao deve ter no maximo 150 caracteres")
        String descricao,

        @CodigoBarras
        @Size(max = 14)
        String codBarraCaixa,   // DUN: obrigatorio, a menos que semDun = true (conferido no ProdutoService)

        @NotBlank(message = "Informe o codigo de barras do produto")
        @CodigoBarras
        @Size(max = 14)
        String codBarraProduto,

        @NotBlank(message = "Informe a classificacao fiscal (NCM)")
        @Pattern(regexp = "\\d{8}", message = "NCM deve conter exatamente 8 digitos")
        String ncm,

        @NotNull(message = "Informe o peso liquido")
        @DecimalMin(value = "0.0001", message = "Peso liquido deve ser maior que zero")
        @Digits(integer = 8, fraction = 4, message = "Peso liquido invalido")
        BigDecimal pesoLiquido,

        @NotNull(message = "Informe o peso bruto")
        @DecimalMin(value = "0.0001", message = "Peso bruto deve ser maior que zero")
        @Digits(integer = 8, fraction = 4, message = "Peso bruto invalido")
        BigDecimal pesoBruto,

        @DecimalMin(value = "0.000001", message = "Metro quadrado da caixa deve ser maior que zero")
        @Digits(integer = 6, fraction = 6, message = "Metro quadrado da caixa invalido")
        BigDecimal metroCaixa,

        @DecimalMin(value = "0.000001", message = "Metro quadrado do pallet deve ser maior que zero")
        @Digits(integer = 6, fraction = 6, message = "Metro quadrado do pallet invalido")
        BigDecimal metroPallet,

        // Fornecedor GERAL informa qtdEmbalagem; fornecedor de piso/revestimento informa m2Caixa.
        // A obrigatoriedade de cada um e verificada no ProdutoService, conforme o tipo do fornecedor.
        @Min(value = 1, message = "Quantidade por embalagem / caixa master deve ser maior que zero")
        @Max(value = 999999999, message = "Quantidade por embalagem / caixa master invalida")
        Integer qtdEmbalagem,

        @DecimalMin(value = "0.0001", message = "m2 por caixa deve ser maior que zero")
        @Digits(integer = 8, fraction = 4, message = "m2 por caixa invalido")
        BigDecimal m2Caixa,

        @DecimalMin(value = "0.0001", message = "m2 por pallet deve ser maior que zero")
        @Digits(integer = 8, fraction = 4, message = "m2 por pallet invalido")
        BigDecimal m2Pallet,

        @Size(max = 500, message = "Observacao deve ter no maximo 500 caracteres")
        String observacao,

        // true = a caixa master nao tem codigo de barras proprio; sem isso o DUN e obrigatorio
        Boolean semDun) {
}
