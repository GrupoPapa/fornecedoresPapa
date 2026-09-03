package br.com.grupopapa.cfp.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Formula;

import java.time.OffsetDateTime;

@Entity
@Table(name = "CFP_PEDIDO")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqPedido")
    @SequenceGenerator(name = "seqPedido", sequenceName = "SEQ_CFP_PEDIDO", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    /** Numero visivel, no formato ANO/SEQUENCIA (ex.: 2026/00001). */
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
    private StatusPedido status = StatusPedido.ABERTO;

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

    @Formula("(select count(*) from CFP_PRODUTO p where p.PEDIDO_ID = ID)")
    private Integer qtdItens;

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

    public Long getId() { return id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public Integer getAno() { return ano; }
    public void setAno(Integer ano) { this.ano = ano; }
    public Integer getSequencia() { return sequencia; }
    public void setSequencia(Integer sequencia) { this.sequencia = sequencia; }
    public Fornecedor getFornecedor() { return fornecedor; }
    public void setFornecedor(Fornecedor fornecedor) { this.fornecedor = fornecedor; }
    public StatusPedido getStatus() { return status; }
    public void setStatus(StatusPedido status) { this.status = status; }
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
    public Integer getQtdItens() { return qtdItens == null ? 0 : qtdItens; }
}
