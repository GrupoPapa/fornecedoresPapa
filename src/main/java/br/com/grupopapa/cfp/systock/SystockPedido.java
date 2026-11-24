package br.com.grupopapa.cfp.systock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Pedido de compra (sugestao) como vem da API externa do Systock. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SystockPedido(
        @JsonProperty("id_pedido") Long idPedido,
        @JsonProperty("id_filial") Long idFilial,
        @JsonProperty("codigo_sistema") String codigoSistema,
        @JsonProperty("id_usuario") Long idUsuario,
        @JsonProperty("nome_usuario") String nomeUsuario,
        @JsonProperty("id_comprador") Long idComprador,
        @JsonProperty("nome_comprador") String nomeComprador,
        @JsonProperty("id_fornecedor") Long idFornecedor,
        @JsonProperty("nome_fornecedor") String nomeFornecedor,
        @JsonProperty("data_abertura") LocalDate dataAbertura,
        @JsonProperty("data_abertura_cotacao") LocalDate dataAberturaCotacao,
        @JsonProperty("status") String status,
        @JsonProperty("observacao") String observacao,
        @JsonProperty("condicao_pagamento") String condicaoPagamento,
        @JsonProperty("produtos") List<Produto> produtos) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Produto(
            @JsonProperty("item") Integer item,
            @JsonProperty("id_produto") String idProduto,
            @JsonProperty("id_fabricante") String idFabricante,
            @JsonProperty("quantidade_sugerida") BigDecimal quantidadeSugerida,
            @JsonProperty("quantidade") BigDecimal quantidade,
            @JsonProperty("preco") BigDecimal preco) { }

    public List<Produto> produtosOuVazio() {
        return produtos == null ? List.of() : produtos;
    }
}
