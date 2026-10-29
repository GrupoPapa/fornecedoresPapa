package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.TipoPedidoCompra;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Cabecalho e itens do pedido de compra. Os itens sao enviados sempre completos. */
public record PedidoCompraRequest(
        @Size(max = 2, message = "Filial invalida") String codFilial,
        TipoPedidoCompra tipo,
        LocalDate dataPrevisaoEntrega,
        @Size(max = 500, message = "Observacao deve ter no maximo 500 caracteres") String observacao,
        @NotNull(message = "Informe os itens do pedido")
        @Size(max = 300, message = "O pedido aceita no maximo 300 itens")
        List<@Valid Item> itens) {

    public record Item(
            Long codProdErp,
            Long produtoId,
            @NotNull(message = "Informe a quantidade")
            @DecimalMin(value = "0.000001", message = "Quantidade deve ser maior que zero")
            @Digits(integer = 12, fraction = 6, message = "Quantidade com no maximo 6 casas decimais")
            BigDecimal quantidade,
            @NotNull(message = "Informe o preco")
            @DecimalMin(value = "0.000001", message = "Preco deve ser maior que zero")
            @Digits(integer = 12, fraction = 6, message = "Preco com no maximo 6 casas decimais")
            BigDecimal precoUnitario,
            @Size(max = 150, message = "Observacao do item com no maximo 150 caracteres") String observacao) { }
}
