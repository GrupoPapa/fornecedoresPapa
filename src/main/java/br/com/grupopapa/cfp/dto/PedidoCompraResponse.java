package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

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
        List<Item> itens) {

    public record Item(
            Long id, Integer seq, Long codProdErp, Long produtoId, StatusProduto produtoStatus,
            String codigoFabrica, String descricao, String embalagem, String unidade,
            BigDecimal quantidade, BigDecimal precoUnitario, BigDecimal valorTotal,
            String observacao, String divergencia, BigDecimal ultimoPreco) {

        static Item de(PedidoCompraItem i) {
            Produto p = i.getProduto();
            return new Item(i.getId(), i.getSeq(), i.getCodProdErp(),
                    p == null ? null : p.getId(), p == null ? null : p.getStatus(),
                    i.getCodigoFabrica(), i.getDescricao(), i.getEmbalagem(), i.getUnidade(),
                    i.getQuantidade(), i.getPrecoUnitario(), i.getValorTotal(),
                    i.getObservacao(), i.getDivergencia(), i.getUltimoPreco());
        }
    }

    public static PedidoCompraResponse resumo(PedidoCompra p) {
        return montar(p, null, p.getQtdItens());
    }

    public static PedidoCompraResponse completo(PedidoCompra p) {
        List<Item> itens = p.getItens().stream().map(Item::de).toList();
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
                itens);
    }
}
