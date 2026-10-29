package br.com.grupopapa.cfp.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Formula;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "CFP_PEDIDO_COMPRA")
public class PedidoCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqPedidoCompra")
    @SequenceGenerator(name = "seqPedidoCompra", sequenceName = "SEQ_CFP_PEDIDO_COMPRA", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "NUMERO", nullable = false, length = 20, unique = true)
    private String numero;

    @Column(name = "ANO", nullable = false)
    private Integer ano;

    @Column(name = "SEQUENCIA", nullable = false)
    private Integer sequencia;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "FORNECEDOR_ID")
    private Fornecedor fornecedor;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private StatusPedidoCompra status = StatusPedidoCompra.RASCUNHO;

    @Column(name = "CODFILIAL", length = 2)
    private String codFilial;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false, length = 20)
    private TipoPedidoCompra tipo = TipoPedidoCompra.NORMAL;

    @Column(name = "DT_PREVISAO_ENTREGA")
    private LocalDate dataPrevisaoEntrega;

    @Column(name = "OBSERVACAO", length = 500)
    private String observacao;

    @Column(name = "PARECER", length = 500)
    private String parecer;

    @Column(name = "VALOR_TOTAL", nullable = false, precision = 18, scale = 2)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(name = "DT_ENVIO")
    private OffsetDateTime dataEnvio;

    @Column(name = "DT_ANALISE")
    private OffsetDateTime dataAnalise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_CRIACAO_ID")
    private Usuario usuarioCriacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_ANALISE_ID")
    private Usuario usuarioAnalise;

    @Column(name = "NUMSUGESTAO_ERP")
    private Long numSugestaoErp;

    @Column(name = "STATUS_INTEGRACAO", nullable = false, length = 20)
    private String statusIntegracao = "PENDENTE";

    @Column(name = "DT_INTEGRACAO")
    private OffsetDateTime dataIntegracao;

    @Column(name = "MENSAGEM_ERRO", length = 1000)
    private String mensagemErro;

    @Column(name = "DT_CRIACAO", nullable = false, updatable = false)
    private OffsetDateTime dataCriacao;

    @Column(name = "DT_ATUALIZACAO")
    private OffsetDateTime dataAtualizacao;

    @Formula("(select count(*) from CFP_PEDIDO_COMPRA_ITEM i where i.PEDIDO_COMPRA_ID = ID)")
    private Integer qtdItens;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    private List<PedidoCompraItem> itens = new ArrayList<>();

    @PrePersist
    void aoCriar() {
        this.dataCriacao = OffsetDateTime.now();
    }

    @PreUpdate
    void aoAtualizar() {
        this.dataAtualizacao = OffsetDateTime.now();
    }

    public boolean isEditavelPeloFornecedor() {
        return status != null && status.isEditavelPeloFornecedor();
    }

    public boolean isSugestaoGerada() {
        return "GERADA".equals(statusIntegracao) && numSugestaoErp != null;
    }

    /** Substitui todos os itens, renumerando a sequencia. */
    public void substituirItens(List<PedidoCompraItem> novos) {
        itens.clear();
        int seq = 1;
        for (PedidoCompraItem item : novos) {
            item.setPedido(this);
            item.setSeq(seq++);
            itens.add(item);
        }
        recalcularTotal();
    }

    public void recalcularTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (PedidoCompraItem item : itens) {
            total = total.add(item.getValorTotal());
        }
        this.valorTotal = total.setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public Integer getAno() { return ano; }
    public void setAno(Integer ano) { this.ano = ano; }
    public Integer getSequencia() { return sequencia; }
    public void setSequencia(Integer sequencia) { this.sequencia = sequencia; }
    public Fornecedor getFornecedor() { return fornecedor; }
    public void setFornecedor(Fornecedor fornecedor) { this.fornecedor = fornecedor; }
    public StatusPedidoCompra getStatus() { return status; }
    public void setStatus(StatusPedidoCompra status) { this.status = status; }
    public String getCodFilial() { return codFilial; }
    public void setCodFilial(String codFilial) { this.codFilial = codFilial; }
    public TipoPedidoCompra getTipo() { return tipo == null ? TipoPedidoCompra.NORMAL : tipo; }
    public void setTipo(TipoPedidoCompra tipo) { this.tipo = tipo; }
    public LocalDate getDataPrevisaoEntrega() { return dataPrevisaoEntrega; }
    public void setDataPrevisaoEntrega(LocalDate v) { this.dataPrevisaoEntrega = v; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
    public String getParecer() { return parecer; }
    public void setParecer(String parecer) { this.parecer = parecer; }
    public BigDecimal getValorTotal() { return valorTotal; }
    public OffsetDateTime getDataEnvio() { return dataEnvio; }
    public void setDataEnvio(OffsetDateTime dataEnvio) { this.dataEnvio = dataEnvio; }
    public OffsetDateTime getDataAnalise() { return dataAnalise; }
    public void setDataAnalise(OffsetDateTime dataAnalise) { this.dataAnalise = dataAnalise; }
    public Usuario getUsuarioCriacao() { return usuarioCriacao; }
    public void setUsuarioCriacao(Usuario usuarioCriacao) { this.usuarioCriacao = usuarioCriacao; }
    public Usuario getUsuarioAnalise() { return usuarioAnalise; }
    public void setUsuarioAnalise(Usuario usuarioAnalise) { this.usuarioAnalise = usuarioAnalise; }
    public Long getNumSugestaoErp() { return numSugestaoErp; }
    public void setNumSugestaoErp(Long numSugestaoErp) { this.numSugestaoErp = numSugestaoErp; }
    public String getStatusIntegracao() { return statusIntegracao; }
    public void setStatusIntegracao(String statusIntegracao) { this.statusIntegracao = statusIntegracao; }
    public OffsetDateTime getDataIntegracao() { return dataIntegracao; }
    public void setDataIntegracao(OffsetDateTime dataIntegracao) { this.dataIntegracao = dataIntegracao; }
    public String getMensagemErro() { return mensagemErro; }
    public void setMensagemErro(String mensagemErro) { this.mensagemErro = mensagemErro; }
    public OffsetDateTime getDataCriacao() { return dataCriacao; }
    public OffsetDateTime getDataAtualizacao() { return dataAtualizacao; }
    public List<PedidoCompraItem> getItens() { return itens; }
    public Integer getQtdItens() { return qtdItens == null ? 0 : qtdItens; }
}
