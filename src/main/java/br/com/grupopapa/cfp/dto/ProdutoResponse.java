package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.Produto;
import br.com.grupopapa.cfp.domain.StatusProduto;
import br.com.grupopapa.cfp.domain.TipoFornecedor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProdutoResponse(
        Long id,
        Long fornecedorId,
        String fornecedorNome,
        Long pedidoId,
        String pedidoNumero,
        TipoFornecedor fornecedorTipo,
        String codigo,
        String descricao,
        String codBarraCaixa,
        String codBarraProduto,
        String ncm,
        BigDecimal pesoLiquido,
        BigDecimal pesoBruto,
        BigDecimal metroCaixa,
        BigDecimal metroPallet,
        Integer qtdEmbalagem,
        BigDecimal m2Caixa,
        BigDecimal m2Pallet,
        StatusProduto status,
        String observacao,
        String parecer,
        boolean editavel,
        Integer qtdFotos,
        OffsetDateTime dataEnvio,
        OffsetDateTime dataAnalise,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataAtualizacao,
        Boolean semDun) {

    public static ProdutoResponse de(Produto p) {
        return new ProdutoResponse(
                p.getId(),
                p.getFornecedor().getId(),
                p.getFornecedor().getRazaoSocial(),
                p.getPedido() != null ? p.getPedido().getId() : null,
                p.getPedido() != null ? p.getPedido().getNumero() : null,
                p.getFornecedor().getTipo(),
                p.getCodigo(),
                p.getDescricao(),
                p.getCodBarraCaixa(),
                p.getCodBarraProduto(),
                p.getNcm(),
                p.getPesoLiquido(),
                p.getPesoBruto(),
                p.getMetroCaixa(),
                p.getMetroPallet(),
                p.getQtdEmbalagem(),
                p.getM2Caixa(),
                p.getM2Pallet(),
                p.getStatus(),
                p.getObservacao(),
                p.getParecer(),
                p.isEditavelPeloFornecedor(),
                p.getQtdFotos(),
                p.getDataEnvio(),
                p.getDataAnalise(),
                p.getDataCriacao(),
                p.getDataAtualizacao(),
                Boolean.TRUE.equals(p.getSemDun()));
    }
}
