package br.com.grupopapa.cfp.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Formula;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "CFP_PRODUTO")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqProduto")
    @SequenceGenerator(name = "seqProduto", sequenceName = "SEQ_CFP_PRODUTO", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "FORNECEDOR_ID")
    private Fornecedor fornecedor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "PEDIDO_ID")
    private Pedido pedido;

    @Column(name = "CODIGO", nullable = false, length = 30)
    private String codigo;

    @Column(name = "DESCRICAO", nullable = false, length = 150)
    private String descricao;

    @Column(name = "COD_BARRA_CAIXA", length = 14)
    private String codBarraCaixa;

    @Column(name = "COD_BARRA_PRODUTO", nullable = false, length = 14)
    private String codBarraProduto;

    @Column(name = "NCM", nullable = false, length = 8)
    private String ncm;

    @Column(name = "PESO_LIQUIDO", nullable = false, precision = 12, scale = 4)
    private BigDecimal pesoLiquido;

    @Column(name = "PESO_BRUTO", nullable = false, precision = 12, scale = 4)
    private BigDecimal pesoBruto;

    @Column(name = "METRO_CAIXA", precision = 12, scale = 6)
    private BigDecimal metroCaixa;

    @Column(name = "METRO_PALLET", precision = 12, scale = 6)
    private BigDecimal metroPallet;

    @Column(name = "QTD_EMBALAGEM")
    private Integer qtdEmbalagem;

    @Column(name = "M2_CAIXA", precision = 12, scale = 4)
    private BigDecimal m2Caixa;

    @Column(name = "M2_PALLET", precision = 12, scale = 4)
    private BigDecimal m2Pallet;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private StatusProduto status = StatusProduto.RASCUNHO;

    @Column(name = "OBSERVACAO", length = 500)
    private String observacao;

    @Column(name = "PARECER", length = 500)
    private String parecer;

    @Column(name = "DT_ENVIO")
    private OffsetDateTime dataEnvio;

    @Column(name = "DT_ANALISE")
    private OffsetDateTime dataAnalise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_ANALISE_ID")
    private Usuario usuarioAnalise;

    @Column(name = "DT_CRIACAO", nullable = false, updatable = false)
    private OffsetDateTime dataCriacao;

    @Column(name = "DT_ATUALIZACAO")
    private OffsetDateTime dataAtualizacao;

    @Formula("(select count(*) from CFP_PRODUTO_FOTO f where f.PRODUTO_ID = ID)")
    private Integer qtdFotos;

    @PrePersist
    void aoCriar() {
        this.dataCriacao = OffsetDateTime.now();
    }

    @PreUpdate
    void aoAtualizar() {
        this.dataAtualizacao = OffsetDateTime.now();
    }

    /** Fornecedor so pode alterar enquanto o cadastro nao foi aprovado. */
    public boolean isEditavelPeloFornecedor() {
        return status == StatusProduto.RASCUNHO || status == StatusProduto.REPROVADO;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Fornecedor getFornecedor() { return fornecedor; }
    public void setFornecedor(Fornecedor fornecedor) { this.fornecedor = fornecedor; }
    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public String getCodBarraCaixa() { return codBarraCaixa; }
    public void setCodBarraCaixa(String codBarraCaixa) { this.codBarraCaixa = codBarraCaixa; }
    public String getCodBarraProduto() { return codBarraProduto; }
    public void setCodBarraProduto(String codBarraProduto) { this.codBarraProduto = codBarraProduto; }
    public String getNcm() { return ncm; }
    public void setNcm(String ncm) { this.ncm = ncm; }
    public BigDecimal getPesoLiquido() { return pesoLiquido; }
    public void setPesoLiquido(BigDecimal pesoLiquido) { this.pesoLiquido = pesoLiquido; }
    public BigDecimal getPesoBruto() { return pesoBruto; }
    public void setPesoBruto(BigDecimal pesoBruto) { this.pesoBruto = pesoBruto; }
    public BigDecimal getMetroCaixa() { return metroCaixa; }
    public void setMetroCaixa(BigDecimal metroCaixa) { this.metroCaixa = metroCaixa; }
    public BigDecimal getMetroPallet() { return metroPallet; }
    public void setMetroPallet(BigDecimal metroPallet) { this.metroPallet = metroPallet; }
    public Integer getQtdEmbalagem() { return qtdEmbalagem; }
    public void setQtdEmbalagem(Integer qtdEmbalagem) { this.qtdEmbalagem = qtdEmbalagem; }
    public BigDecimal getM2Caixa() { return m2Caixa; }
    public void setM2Caixa(BigDecimal m2Caixa) { this.m2Caixa = m2Caixa; }
    public BigDecimal getM2Pallet() { return m2Pallet; }
    public void setM2Pallet(BigDecimal m2Pallet) { this.m2Pallet = m2Pallet; }
    public StatusProduto getStatus() { return status; }
    public void setStatus(StatusProduto status) { this.status = status; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
    public String getParecer() { return parecer; }
    public void setParecer(String parecer) { this.parecer = parecer; }
    public OffsetDateTime getDataEnvio() { return dataEnvio; }
    public void setDataEnvio(OffsetDateTime dataEnvio) { this.dataEnvio = dataEnvio; }
    public OffsetDateTime getDataAnalise() { return dataAnalise; }
    public void setDataAnalise(OffsetDateTime dataAnalise) { this.dataAnalise = dataAnalise; }
    public Usuario getUsuarioAnalise() { return usuarioAnalise; }
    public void setUsuarioAnalise(Usuario usuarioAnalise) { this.usuarioAnalise = usuarioAnalise; }
    public OffsetDateTime getDataCriacao() { return dataCriacao; }
    public OffsetDateTime getDataAtualizacao() { return dataAtualizacao; }
    public Integer getQtdFotos() { return qtdFotos == null ? 0 : qtdFotos; }
}
