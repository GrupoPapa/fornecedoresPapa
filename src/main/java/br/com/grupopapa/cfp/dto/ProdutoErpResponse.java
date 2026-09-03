package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.ProdutoErp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProdutoErpResponse(
        Long produtoId,
        String descricao, Integer codMarca, Integer codEpto, Integer codSec,
        String codFab, String codNcmEx, String classificacaoFiscal,
        String embalagem, String unidade, BigDecimal qtUnit,
        String unidadeMaster, BigDecimal qtUnitCx, BigDecimal multiplo,
        BigDecimal pesoLiq, BigDecimal pesoBruto,
        String codAuxiliar, Integer gtinCodAuxiliar,
        Integer sugVenda, BigDecimal pComExt1, BigDecimal pComInt1, BigDecimal pComRep1,
        String revenda, Integer temRepos, String cestaBasicaLegis,
        String aceitaVendaFracao, String enviaInfTecnicaNfe,
        String enviaEcommerce, String usaWms,
        String tipoCargaProduto, String localSeparacao, String naturezaProduto,
        boolean completo, boolean salvo,
        String statusIntegracao, Long codProdErp,
        OffsetDateTime dataIntegracao, String mensagemErro) {

    public static ProdutoErpResponse de(ProdutoErp e, boolean salvo) {
        return new ProdutoErpResponse(
                e.getProdutoId(),
                e.getDescricao(), e.getCodMarca(), e.getCodEpto(), e.getCodSec(),
                e.getCodFab(), e.getCodNcmEx(), e.getClassificacaoFiscal(),
                e.getEmbalagem(), e.getUnidade(), e.getQtUnit(),
                e.getUnidadeMaster(), e.getQtUnitCx(), e.getMultiplo(),
                e.getPesoLiq(), e.getPesoBruto(),
                e.getCodAuxiliar(), e.getGtinCodAuxiliar(),
                e.getSugVenda(), e.getPComExt1(), e.getPComInt1(), e.getPComRep1(),
                e.getRevenda(), e.getTemRepos(), e.getCestaBasicaLegis(),
                e.getAceitaVendaFracao(), e.getEnviaInfTecnicaNfe(),
                e.getEnviaEcommerce(), e.getUsaWms(),
                e.getTipoCargaProduto(), e.getLocalSeparacao(), e.getNaturezaProduto(),
                e.isCompleto(), salvo,
                e.getStatusIntegracao(), e.getCodProdErp(),
                e.getDataIntegracao(), e.getMensagemErro());
    }
}
