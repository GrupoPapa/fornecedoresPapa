package br.com.grupopapa.cfp.impressao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Pedido de compra do WinThor (PCPEDIDO/PCITEM) com os dados que a rotina 210
 * ("Emitir Pedido de Compra") imprime. Campos que a base nao tiver vem nulos.
 */
public record PedidoWinthorImpressao(
        Long numPed,
        LocalDate data,
        String unidadeEmissao,          // "UNID. DE VENDA" / "UNID. DE COMPRA" (null = nao informado)
        String tipoPedido,              // "COMPRA MERC." / "BONIFICACAO"
        Empresa empresa,
        Fornecedor fornecedor,
        String transportador,
        String condicaoPagamento,       // "28, 42, 56"
        String frete,                   // CIF / FOB
        LocalDate dataFaturamento,
        LocalDate dataEmbarque,
        LocalDate dataEntrega,
        List<Item> itens,
        BigDecimal totalBruto,
        BigDecimal descontoSuframa,
        BigDecimal totalLiquido,
        BigDecimal baseIcms,
        BigDecimal valorIcms,
        BigDecimal volumes,
        BigDecimal peso,
        String incluiuPedido,
        String alterouPedido,
        String observacoes,
        String origem,                  // ex.: "220 - Importacao Sugestao Compra ... Num. 2258"
        List<String> rodape) {

    public record Empresa(String codigo, String razaoSocial, String cnpj, String ie, String endereco,
                          String cidade, String uf, String cep, String suframa) { }

    public record Fornecedor(Long codigo, String nome, String cnpj, String endereco, String bairro,
                             String cidade, String uf, String cep, String telefone) { }

    public record Item(Long codProd, String codFab, String descricao, String embalagem, String unidade,
                       BigDecimal qtPedida, BigDecimal valorIpi, BigDecimal precoLiquido, BigDecimal valorTotal) { }
}
