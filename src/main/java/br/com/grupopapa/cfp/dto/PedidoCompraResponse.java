package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record PedidoCompraResponse(
        Long id,
        String numero,
        Long fornecedorId,
        String fornecedorNome,
        Long codFornecErp,
        StatusPedidoCompra status,
        String codFilial,
        boolean filialFixa,
        TipoPedidoCompra tipo,
        LocalDate dataPrevisaoEntrega,
        String observacao,
        String parecer,
        BigDecimal valorTotal,
        Integer qtdItens,
        boolean editavel,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataEnvio,
        OffsetDateTime dataAnalise,
        Long numSugestaoErp,
        String statusIntegracao,
        String mensagemErro,
        OffsetDateTime dataIntegracao,
        List<Item> itens,
        Long systockId) {

    public record Item(
            Long id, Integer seq, Long codProdErp, Long produtoId, StatusProduto produtoStatus,
            String codigoFabrica, String descricao, String embalagem, String unidade,
            BigDecimal quantidade, BigDecimal precoUnitario, BigDecimal valorTotal,
            String observacao, String divergencia, BigDecimal ultimoPreco,
            BigDecimal qtdeSugerida, BigDecimal qtdeSystock, BigDecimal precoSystock,
            UltimaEntrada ultimaEntrada, String fornecedorProduto) {

        static Item de(PedidoCompraItem i, Extras extras) {
            Produto p = i.getProduto();
            Long cod = i.getCodProdErp();
            return new Item(i.getId(), i.getSeq(), cod,
                    p == null ? null : p.getId(), p == null ? null : p.getStatus(),
                    i.getCodigoFabrica(), i.getDescricao(), i.getEmbalagem(), i.getUnidade(),
                    i.getQuantidade(), i.getPrecoUnitario(), i.getValorTotal(),
                    i.getObservacao(), i.getDivergencia(), i.getUltimoPreco(),
                    valor(i.getQtdeSugerida(), i.getQtdeSystock(), extras, cod, 0),
                    valor(i.getQtdeSugerida(), i.getQtdeSystock(), extras, cod, 1),
                    i.getQtdeSugerida() != null || i.getQtdeSystock() != null ? i.getPrecoSystock()
                            : valor(null, null, extras, cod, 2),
                    cod == null ? null : extras.ultimasEntradas().get(cod),
                    cod == null ? null : extras.fornecedorProduto().get(cod));
        }
    }

    /** Valor gravado no item ou, se o item nao tem nada gravado, o lido agora na sugestao do Systock. */
    private static BigDecimal valor(BigDecimal sugerida, BigDecimal qtdSystock, Extras extras, Long cod, int pos) {
        if (sugerida != null || qtdSystock != null) {
            return pos == 0 ? sugerida : qtdSystock;
        }
        BigDecimal[] o = cod == null ? null : extras.systock().get(cod);
        return o == null ? null : o[pos];
    }

    /** Ultima entrada (nota de compra) do produto no WinThor, em qualquer filial. */
    public record UltimaEntrada(LocalDate data, BigDecimal valorUnitario, BigDecimal quantidade,
                                String numNota, String codFilial, Long codFornec, String fornecedor) { }

    /**
     * Dados lidos do WinThor na hora de montar a tela (nao ficam gravados no pedido).
     * @param fornecedorProduto codProd -> "codigo - nome" quando o produto e de outro fornecedor do grupo
     * @param systock           codProd -> [qtde sugerida, qtde, preco] lidos na sugestao do Systock, so
     *                          para pedidos trazidos antes de os itens guardarem esses dados
     */
    public record Extras(Map<Long, UltimaEntrada> ultimasEntradas, Map<Long, String> fornecedorProduto,
                         Map<Long, BigDecimal[]> systock) {
        public static final Extras VAZIO = new Extras(Map.of(), Map.of(), Map.of());
    }

    public static PedidoCompraResponse resumo(PedidoCompra p) {
        return montar(p, null, p.getQtdItens());
    }

    public static PedidoCompraResponse completo(PedidoCompra p) {
        return completo(p, Extras.VAZIO);
    }

    public static PedidoCompraResponse completo(PedidoCompra p, Extras extras) {
        List<Item> itens = p.getItens().stream().map(i -> Item.de(i, extras)).toList();
        return montar(p, itens, itens.size());
    }

    private static PedidoCompraResponse montar(PedidoCompra p, List<Item> itens, int qtd) {
        Fornecedor f = p.getFornecedor();
        return new PedidoCompraResponse(
                p.getId(), p.getNumero(), f.getId(), f.getRazaoSocial(), f.getCodFornecedorErp(),
                p.getStatus(), p.getCodFilial(), f.getCodFilialCompra() != null, p.getTipo(), p.getDataPrevisaoEntrega(),
                p.getObservacao(), p.getParecer(), p.getValorTotal(), qtd,
                p.isEditavelPeloFornecedor(), p.getDataCriacao(), p.getDataEnvio(), p.getDataAnalise(),
                p.getNumSugestaoErp(), p.getStatusIntegracao(), p.getMensagemErro(), p.getDataIntegracao(),
                itens, p.getSystockId());
    }
}
