package br.com.grupopapa.cfp.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "CFP_PEDIDO_COMPRA_ITEM")
public class PedidoCompraItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqPedidoCompraItem")
    @SequenceGenerator(name = "seqPedidoCompraItem", sequenceName = "SEQ_CFP_PEDIDO_COMPRA_ITEM", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PEDIDO_COMPRA_ID")
    private PedidoCompra pedido;

    @Column(name = "SEQ", nullable = false)
    private Integer seq;

    /** CODPROD do WinThor. Nulo enquanto o produto novo nao for gravado no ERP. */
    @Column(name = "CODPROD_ERP")
    private Long codProdErp;

    /** Cadastro do portal, quando o item e um produto novo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUTO_ID")
    private Produto produto;

    @Column(name = "CODIGO_FABRICA", length = 30)
    private String codigoFabrica;

    @Column(name = "DESCRICAO", nullable = false, length = 150)
    private String descricao;

    @Column(name = "EMBALAGEM", length = 20)
    private String embalagem;

    @Column(name = "UNIDADE", length = 5)
    private String unidade;

    @Column(name = "QUANTIDADE", nullable = false, precision = 18, scale = 6)
    private BigDecimal quantidade;

    @Column(name = "PRECO_UNITARIO", nullable = false, precision = 18, scale = 6)
    private BigDecimal precoUnitario;

    @Column(name = "OBSERVACAO", length = 200)
    private String observacao;

    @Column(name = "DIVERGENCIA", length = 500)
    private String divergencia;

    @Column(name = "ULTIMO_PRECO", precision = 18, scale = 6)
    private BigDecimal ultimoPreco;

    public BigDecimal getValorTotal() {
        if (quantidade == null || precoUnitario == null) {
            return BigDecimal.ZERO;
        }
        return quantidade.multiply(precoUnitario).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isProdutoNovo() {
        return codProdErp == null;
    }

    public Long getId() { return id; }
    public PedidoCompra getPedido() { return pedido; }
    public void setPedido(PedidoCompra pedido) { this.pedido = pedido; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public Long getCodProdErp() { return codProdErp; }
    public void setCodProdErp(Long codProdErp) { this.codProdErp = codProdErp; }
    public Produto getProduto() { return produto; }
    public void setProduto(Produto produto) { this.produto = produto; }
    public String getCodigoFabrica() { return codigoFabrica; }
    public void setCodigoFabrica(String codigoFabrica) { this.codigoFabrica = codigoFabrica; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public String getEmbalagem() { return embalagem; }
    public void setEmbalagem(String embalagem) { this.embalagem = embalagem; }
    public String getUnidade() { return unidade; }
    public void setUnidade(String unidade) { this.unidade = unidade; }
    public BigDecimal getQuantidade() { return quantidade; }
    public void setQuantidade(BigDecimal quantidade) { this.quantidade = quantidade; }
    public BigDecimal getPrecoUnitario() { return precoUnitario; }
    public void setPrecoUnitario(BigDecimal precoUnitario) { this.precoUnitario = precoUnitario; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
    public String getDivergencia() { return divergencia; }
    public void setDivergencia(String divergencia) { this.divergencia = divergencia; }
    public BigDecimal getUltimoPreco() { return ultimoPreco; }
    public void setUltimoPreco(BigDecimal ultimoPreco) { this.ultimoPreco = ultimoPreco; }
}
