package br.com.grupopapa.cfp.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * FAR - controle de avarias. Cada registro e uma linha da planilha de controle:
 * visita, quantidade, motivo, abertura da FAR, situacao, observacoes e o vinculo
 * com a baixa fiscal da avaria no sistema (este ultimo, so a equipe interna marca).
 */
@Entity
@Table(name = "CFP_FAR")
public class Far {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqFar")
    @SequenceGenerator(name = "seqFar", sequenceName = "SEQ_CFP_FAR", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "FORNECEDOR_ID")
    private Fornecedor fornecedor;

    @Column(name = "DT_VISITA", nullable = false)
    private LocalDate dataVisita;

    @Column(name = "CODIGO_PRODUTO", length = 200)
    private String codigoProduto;

    @Column(name = "QTD_PRODUTOS", nullable = false)
    private Integer qtdProdutos;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MOTIVO_ID")
    private FarMotivo motivo;

    @Column(name = "OBS_OCORRENCIA", length = 1000)
    private String obsOcorrencia;

    @Column(name = "DT_ABERTURA_FAR")
    private LocalDate dataAberturaFar;

    @Column(name = "NUMERO_FAR", length = 30)
    private String numeroFar;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 30)
    private StatusFar status = StatusFar.EM_ANALISE;

    @Column(name = "STATUS_OUTROS", length = 100)
    private String statusOutros;

    @Column(name = "OBS_FAR_FORNECEDOR", length = 1000)
    private String obsFarFornecedor;

    @Column(name = "OBS_FAR_EMPRESA", length = 1000)
    private String obsFarEmpresa;

    @Column(name = "BAIXA_FISCAL", nullable = false)
    private Boolean baixaFiscal = Boolean.FALSE;

    @Column(name = "BAIXA_FISCAL_REF", length = 100)
    private String baixaFiscalReferencia;

    @Column(name = "BAIXA_FISCAL_OBS", length = 500)
    private String baixaFiscalObservacao;

    @Column(name = "DT_BAIXA_FISCAL")
    private OffsetDateTime dataBaixaFiscal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_BAIXA_ID")
    private Usuario usuarioBaixa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_CRIACAO_ID")
    private Usuario usuarioCriacao;

    @Column(name = "DT_CRIACAO", nullable = false, updatable = false)
    private OffsetDateTime dataCriacao;

    @Column(name = "DT_ATUALIZACAO")
    private OffsetDateTime dataAtualizacao;

    @PrePersist
    void aoCriar() {
        this.dataCriacao = OffsetDateTime.now();
    }

    @PreUpdate
    void aoAtualizar() {
        this.dataAtualizacao = OffsetDateTime.now();
    }

    /** Depois do vinculo com a baixa fiscal o fornecedor nao altera mais a FAR. */
    public boolean isEditavelPeloFornecedor() {
        return !Boolean.TRUE.equals(baixaFiscal);
    }

    public Long getId() { return id; }
    public Fornecedor getFornecedor() { return fornecedor; }
    public void setFornecedor(Fornecedor fornecedor) { this.fornecedor = fornecedor; }
    public LocalDate getDataVisita() { return dataVisita; }
    public void setDataVisita(LocalDate dataVisita) { this.dataVisita = dataVisita; }
    public String getCodigoProduto() { return codigoProduto; }
    public void setCodigoProduto(String codigoProduto) { this.codigoProduto = codigoProduto; }
    public Integer getQtdProdutos() { return qtdProdutos; }
    public void setQtdProdutos(Integer qtdProdutos) { this.qtdProdutos = qtdProdutos; }
    public FarMotivo getMotivo() { return motivo; }
    public void setMotivo(FarMotivo motivo) { this.motivo = motivo; }
    public String getObsOcorrencia() { return obsOcorrencia; }
    public void setObsOcorrencia(String obsOcorrencia) { this.obsOcorrencia = obsOcorrencia; }
    public LocalDate getDataAberturaFar() { return dataAberturaFar; }
    public void setDataAberturaFar(LocalDate dataAberturaFar) { this.dataAberturaFar = dataAberturaFar; }
    public String getNumeroFar() { return numeroFar; }
    public void setNumeroFar(String numeroFar) { this.numeroFar = numeroFar; }
    public StatusFar getStatus() { return status; }
    public void setStatus(StatusFar status) { this.status = status; }
    public String getStatusOutros() { return statusOutros; }
    public void setStatusOutros(String statusOutros) { this.statusOutros = statusOutros; }
    public String getObsFarFornecedor() { return obsFarFornecedor; }
    public void setObsFarFornecedor(String obsFarFornecedor) { this.obsFarFornecedor = obsFarFornecedor; }
    public String getObsFarEmpresa() { return obsFarEmpresa; }
    public void setObsFarEmpresa(String obsFarEmpresa) { this.obsFarEmpresa = obsFarEmpresa; }
    public Boolean getBaixaFiscal() { return baixaFiscal; }
    public void setBaixaFiscal(Boolean baixaFiscal) { this.baixaFiscal = baixaFiscal; }
    public String getBaixaFiscalReferencia() { return baixaFiscalReferencia; }
    public void setBaixaFiscalReferencia(String v) { this.baixaFiscalReferencia = v; }
    public String getBaixaFiscalObservacao() { return baixaFiscalObservacao; }
    public void setBaixaFiscalObservacao(String v) { this.baixaFiscalObservacao = v; }
    public OffsetDateTime getDataBaixaFiscal() { return dataBaixaFiscal; }
    public void setDataBaixaFiscal(OffsetDateTime v) { this.dataBaixaFiscal = v; }
    public Usuario getUsuarioBaixa() { return usuarioBaixa; }
    public void setUsuarioBaixa(Usuario usuarioBaixa) { this.usuarioBaixa = usuarioBaixa; }
    public Usuario getUsuarioCriacao() { return usuarioCriacao; }
    public void setUsuarioCriacao(Usuario usuarioCriacao) { this.usuarioCriacao = usuarioCriacao; }
    public OffsetDateTime getDataCriacao() { return dataCriacao; }
    public OffsetDateTime getDataAtualizacao() { return dataAtualizacao; }
}
