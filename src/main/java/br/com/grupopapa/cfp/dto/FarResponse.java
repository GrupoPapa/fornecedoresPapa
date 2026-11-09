package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.Far;
import br.com.grupopapa.cfp.domain.FarItem;
import br.com.grupopapa.cfp.domain.StatusFar;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record FarResponse(
        Long id,
        Long fornecedorId, String fornecedorNome, Long codFornecedorErp,
        LocalDate dataVisita,
        List<Item> itens, Integer qtdProdutos, String produtosResumo, String motivosResumo,
        String obsOcorrencia,
        LocalDate dataAberturaFar, String numeroFar, String numeroNota, String numeroPedido,
        StatusFar status, String statusRotulo, String statusOutros,
        String obsFarFornecedor, String obsFarEmpresa,
        Boolean baixaFiscal, String baixaFiscalReferencia, String baixaFiscalObservacao,
        OffsetDateTime dataBaixaFiscal, String usuarioBaixa,
        long qtdComprovantes, boolean comprovantePendente,
        boolean editavel,
        String usuarioCriacao, OffsetDateTime dataCriacao, OffsetDateTime dataAtualizacao) {

    public record Item(Long id, Integer seq, String codigoProduto, String descricaoProduto, Integer quantidade,
                       Long motivoId, String motivoDescricao, Boolean motivoAtivo) {

        static Item de(FarItem i) {
            return new Item(i.getId(), i.getSeq(), i.getCodigoProduto(), i.getDescricaoProduto(), i.getQuantidade(),
                    i.getMotivo().getId(), i.getMotivo().getDescricao(), i.getMotivo().getAtivo());
        }
    }

    /**
     * @param itens    itens da FAR, com o motivo ja carregado
     * @param editavel se o usuario logado pode alterar a FAR
     */
    public static FarResponse de(Far f, List<FarItem> itens, long qtdComprovantes, boolean editavel, boolean completo) {
        String produtos = itens.stream().map(FarItem::getCodigoProduto).collect(Collectors.joining(", "));
        String motivos = itens.stream().map(i -> i.getMotivo().getDescricao()).distinct()
                .collect(Collectors.joining(", "));
        return new FarResponse(
                f.getId(),
                f.getFornecedor().getId(), f.getFornecedor().getRazaoSocial(), f.getFornecedor().getCodFornecedorErp(),
                f.getDataVisita(),
                itens.stream().map(Item::de).toList(), f.getQtdProdutos(), produtos, motivos,
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
