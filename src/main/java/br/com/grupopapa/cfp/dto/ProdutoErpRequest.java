package br.com.grupopapa.cfp.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Campos do WinThor informados na analise do pedido. */
public record ProdutoErpRequest(
        @Size(max = 40, message = "Descricao do WinThor: maximo de 40 caracteres") String descricao,
        Integer codMarca,
        Integer codEpto,
        Integer codSec,
        @Size(max = 30) String codFab,
        @Size(max = 20) String codNcmEx,
        @Size(max = 10) String classificacaoFiscal,

        @Size(max = 12, message = "Embalagem: maximo de 12 caracteres") String embalagem,
        @Size(max = 2, message = "Unidade: maximo de 2 caracteres") String unidade,
        @Digits(integer = 4, fraction = 2) BigDecimal qtUnit,
        @Size(max = 2, message = "Unidade master: maximo de 2 caracteres") String unidadeMaster,
        @Digits(integer = 6, fraction = 2) BigDecimal qtUnitCx,
        @Digits(integer = 12, fraction = 6) BigDecimal multiplo,
        @Digits(integer = 6, fraction = 6) BigDecimal pesoLiq,
        @Digits(integer = 6, fraction = 6) BigDecimal pesoBruto,

        @Size(max = 14) String codAuxiliar,
        // padrao do GTIN: 8, 12, 13 ou 14 (PCPRODUT.GTINCODAUXILIAR e NUMBER(2))
        @Min(0) @Max(99) Integer gtinCodAuxiliar,

        // PCPRODUT.SUGVENDA e NUMBER(2): codigo, nao valor
        @Min(0) @Max(99) Integer sugVenda,
        @Digits(integer = 4, fraction = 2) BigDecimal pComExt1,
        @Digits(integer = 4, fraction = 2) BigDecimal pComInt1,
        @Digits(integer = 4, fraction = 2) BigDecimal pComRep1,

        @Pattern(regexp = "[SN]?", message = "Informe S ou N") String revenda,
        // PCPRODUT.TEMREPOS e NUMBER(4): tempo de reposicao em dias
        @Min(0) @Max(9999) Integer temRepos,
        @Pattern(regexp = "[SN]?", message = "Informe S ou N") String cestaBasicaLegis,
        @Pattern(regexp = "[SN]?", message = "Informe S ou N") String aceitaVendaFracao,
        @Pattern(regexp = "[SN]?", message = "Informe S ou N") String enviaInfTecnicaNfe,
        @Pattern(regexp = "[SN]?", message = "Informe S ou N") String enviaEcommerce,
        @Pattern(regexp = "[SN]?", message = "Informe S ou N") String usaWms,

        @Pattern(regexp = "[AEG]?", message = "Tipo de carga: A, E ou G") String tipoCargaProduto,
        @Pattern(regexp = "(I|EF|EG)?", message = "Local de separacao: I, EF ou EG") String localSeparacao,
        // opcoes da rotina 203: ME = medicamentos, OT = outros, SS = servicos, VC = veiculo
        @Pattern(regexp = "(ME|OT|SS|VC)?",
                 message = "Natureza do produto: ME, OT, SS ou VC") String naturezaProduto) {
}
