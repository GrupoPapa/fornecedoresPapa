package br.com.grupopapa.cfp.domain;

import jakarta.persistence.*;

/** Produto da FAR: cada um tem codigo, quantidade e motivo da avaria proprios. */
@Entity
@Table(name = "CFP_FAR_ITEM")
public class FarItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqFarItem")
    @SequenceGenerator(name = "seqFarItem", sequenceName = "SEQ_CFP_FAR_ITEM", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "FAR_ID")
    private Far far;

    @Column(name = "SEQ", nullable = false)
    private Integer seq;

    @Column(name = "CODIGO_PRODUTO", nullable = false, length = 60)
    private String codigoProduto;

    @Column(name = "DESCRICAO_PRODUTO", length = 200)
    private String descricaoProduto;

    @Column(name = "QUANTIDADE", nullable = false)
    private Integer quantidade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MOTIVO_ID")
    private FarMotivo motivo;

    public Long getId() { return id; }
    public Far getFar() { return far; }
    public void setFar(Far far) { this.far = far; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public String getCodigoProduto() { return codigoProduto; }
    public void setCodigoProduto(String codigoProduto) { this.codigoProduto = codigoProduto; }
    public String getDescricaoProduto() { return descricaoProduto; }
    public void setDescricaoProduto(String descricaoProduto) { this.descricaoProduto = descricaoProduto; }
    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }
    public FarMotivo getMotivo() { return motivo; }
    public void setMotivo(FarMotivo motivo) { this.motivo = motivo; }
}
