package br.com.grupopapa.cfp.systock;

import br.com.grupopapa.cfp.domain.StatusPedidoCompra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Sugestoes de compra do Systock para a tela "Pedidos de compra > Sugestoes do Systock". */
public record SugestoesSystockResponse(boolean habilitado, String aviso, int diasFinalizados,
                                       List<Sugestao> sugestoes) {

    static SugestoesSystockResponse desligado() {
        return new SugestoesSystockResponse(false,
                "A consulta ao Systock nao esta configurada (variavel CFP_SYSTOCK_TOKEN).", 0, List.of());
    }

    static SugestoesSystockResponse comAviso(int dias, String aviso) {
        return new SugestoesSystockResponse(true, aviso, dias, List.of());
    }

    /**
     * @param statusCodigo AB, CT, PARA_EXPORTACAO ou FN
     * @param importado    pedido do portal criado a partir desta sugestao (null se ainda nao foi trazida)
     */
    public record Sugestao(Long idPedido, String statusCodigo, String statusRotulo, String statusSystock,
                           LocalDate dataAbertura, String usuario,
                           Long idFornecedorErp, String nomeFornecedor, Long fornecedorIdPortal,
                           Long idFilial, String observacao, String condicaoPagamento,
                           int qtdItens, BigDecimal valorTotal, List<Item> itens, Importado importado) { }

    /**
     * @param quantidadeSugerida quantidade que o Systock calculou (o comprador pode ter mudado para {@code quantidade})
     * @param ultimaEntrada      ultima entrada do produto no WinThor (PCMOV), null se nunca entrou
     * @param foraDoFornecedor   o produto, no WinThor, e de um fornecedor fora do grupo (o portal vai recusar)
     * @param fornecedorProduto  "codigo - nome" do fornecedor do produto no WinThor quando e outro do
     *                           mesmo grupo (principal ou secundario); null quando e o proprio fornecedor
     */
    public record Item(Integer item, String idProduto, Long codProd, String descricao, String codigoFabrica,
                       String embalagem, BigDecimal quantidadeSugerida, BigDecimal quantidade, BigDecimal preco,
                       BigDecimal valorTotal, UltimaEntrada ultimaEntrada, boolean foraDoFornecedor,
                       String fornecedorProduto) { }

    /** Ultima entrada (nota de compra) do produto no WinThor, em qualquer filial. */
    public record UltimaEntrada(LocalDate data, BigDecimal valorUnitario, BigDecimal quantidade,
                                String numNota, String codFilial, Long codFornec, String fornecedor) { }

    public record Importado(Long pedidoId, String numero, StatusPedidoCompra status) { }
}
