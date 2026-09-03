package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.Pedido;
import br.com.grupopapa.cfp.domain.StatusPedido;
import br.com.grupopapa.cfp.domain.TipoFornecedor;

import java.time.OffsetDateTime;
import java.util.List;

public record PedidoResponse(
        Long id,
        String numero,
        Long fornecedorId,
        String fornecedorNome,
        TipoFornecedor fornecedorTipo,
        StatusPedido status,
        String observacao,
        String parecer,
        boolean editavel,
        Integer qtdItens,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataEnvio,
        OffsetDateTime dataAnalise,
        List<ProdutoResponse> itens) {

    public static PedidoResponse de(Pedido p) {
        return montar(p, null);
    }

    public static PedidoResponse comItens(Pedido p, List<ProdutoResponse> itens) {
        return montar(p, itens);
    }

    private static PedidoResponse montar(Pedido p, List<ProdutoResponse> itens) {
        return new PedidoResponse(
                p.getId(),
                p.getNumero(),
                p.getFornecedor().getId(),
                p.getFornecedor().getRazaoSocial(),
                p.getFornecedor().getTipo(),
                p.getStatus(),
                p.getObservacao(),
                p.getParecer(),
                p.isEditavelPeloFornecedor(),
                itens != null ? itens.size() : p.getQtdItens(),
                p.getDataCriacao(),
                p.getDataEnvio(),
                p.getDataAnalise(),
                itens);
    }
}
