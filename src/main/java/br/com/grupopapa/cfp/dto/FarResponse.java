package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.Far;
import br.com.grupopapa.cfp.domain.StatusFar;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record FarResponse(
        Long id,
        Long fornecedorId, String fornecedorNome, Long codFornecedorErp,
        LocalDate dataVisita, String codigoProduto, Integer qtdProdutos,
        Long motivoId, String motivoDescricao,
        String obsOcorrencia,
        LocalDate dataAberturaFar, String numeroFar, String numeroNota, String numeroPedido,
        StatusFar status, String statusRotulo, String statusOutros,
        String obsFarFornecedor, String obsFarEmpresa,
        Boolean baixaFiscal, String baixaFiscalReferencia, String baixaFiscalObservacao,
        OffsetDateTime dataBaixaFiscal, String usuarioBaixa,
        long qtdComprovantes, boolean comprovantePendente,
        boolean editavel,
        String usuarioCriacao, OffsetDateTime dataCriacao, OffsetDateTime dataAtualizacao) {

    /** @param editavel se o usuario logado pode alterar a FAR */
    public static FarResponse de(Far f, long qtdComprovantes, boolean editavel, boolean completo) {
        return new FarResponse(
                f.getId(),
                f.getFornecedor().getId(), f.getFornecedor().getRazaoSocial(), f.getFornecedor().getCodFornecedorErp(),
                f.getDataVisita(), f.getCodigoProduto(), f.getQtdProdutos(),
                f.getMotivo().getId(), f.getMotivo().getDescricao(),
                f.getObsOcorrencia(),
                f.getDataAberturaFar(), f.getNumeroFar(), f.getNumeroNota(), f.getNumeroPedido(),
                f.getStatus(), f.getStatus().getRotulo(), f.getStatusOutros(),
                f.getObsFarFornecedor(), f.getObsFarEmpresa(),
                f.getBaixaFiscal(), f.getBaixaFiscalReferencia(), f.getBaixaFiscalObservacao(),
                f.getDataBaixaFiscal(),
                completo && f.getUsuarioBaixa() != null ? f.getUsuarioBaixa().getNome() : null,
                qtdComprovantes,
                f.getStatus() == StatusFar.FINALIZADO_APROVADO && qtdComprovantes == 0,
                editavel,
                completo && f.getUsuarioCriacao() != null ? f.getUsuarioCriacao().getNome() : null,
                f.getDataCriacao(), f.getDataAtualizacao());
    }
}
