package br.com.grupopapa.cfp.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Campos do WinThor preenchidos pela equipe interna antes de gravar na PCPRODUT. */
@Entity
@Table(name = "CFP_PRODUTO_ERP")
public class ProdutoErp {

    @Id
    @Column(name = "PRODUTO_ID")
    private Long produtoId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PRODUTO_ID")
    private Produto produto;

    @Column(name = "DESCRICAO", length = 40) private String descricao;          // PCPRODUT VARCHAR2(40)
    @Column(name = "CODMARCA") private Integer codMarca;
    @Column(name = "CODEPTO") private Integer codEpto;
    @Column(name = "CODSEC") private Integer codSec;
    @Column(name = "CODFAB", length = 30) private String codFab;                // PCPRODUT VARCHAR2(30)
    @Column(name = "CODNCMEX", length = 20) private String codNcmEx;
    @Column(name = "CLASSIFICFISCAL", length = 10) private String classificacaoFiscal;

    @Column(name = "EMBALAGEM", length = 12) private String embalagem;          // PCPRODUT VARCHAR2(12)
    @Column(name = "UNIDADE", length = 2) private String unidade;               // PCPRODUT VARCHAR2(2)
    @Column(name = "QTUNIT", precision = 6, scale = 2) private BigDecimal qtUnit;        // NUMBER(6,2)
    @Column(name = "UNIDADEMASTER", length = 2) private String unidadeMaster;   // PCPRODUT VARCHAR2(2)
    @Column(name = "QTUNITCX", precision = 8, scale = 2) private BigDecimal qtUnitCx;    // NUMBER(8,2)
    @Column(name = "MULTIPLO", precision = 18, scale = 6) private BigDecimal multiplo;   // NUMBER(18,6)
    @Column(name = "PESOLIQ", precision = 12, scale = 6) private BigDecimal pesoLiq;     // NUMBER(12,6)
    @Column(name = "PESOBRUTO", precision = 12, scale = 6) private BigDecimal pesoBruto; // NUMBER(12,6)

    @Column(name = "CODAUXILIAR", length = 14) private String codAuxiliar;
    // PCPRODUT.GTINCODAUXILIAR e NUMBER(2): guarda o PADRAO do GTIN (8, 12, 13 ou 14),
    // nao o codigo de barras - este vai em CODAUXILIAR.
    @Column(name = "GTINCODAUXILIAR") private Integer gtinCodAuxiliar;

    // PCPRODUT.SUGVENDA e NUMBER(2): codigo de "sugerir preco de venda baseado em", nao um valor
    @Column(name = "SUGVENDA") private Integer sugVenda;
    @Column(name = "PCOMEXT1", precision = 6, scale = 2) private BigDecimal pComExt1;    // NUMBER(6,2)
    @Column(name = "PCOMINT1", precision = 6, scale = 2) private BigDecimal pComInt1;    // NUMBER(6,2)
    @Column(name = "PCOMREP1", precision = 6, scale = 2) private BigDecimal pComRep1;    // NUMBER(6,2)
    @Column(name = "REVENDA", length = 1) private String revenda;
    // PCPRODUT.TEMREPOS e NUMBER(4): TEMPO de reposicao em DIAS (30, 21, 45, 60...)
    @Column(name = "TEMREPOS") private Integer temRepos;
    @Column(name = "CESTABASICALEGIS", length = 1) private String cestaBasicaLegis;
    @Column(name = "ACEITAVENDAFRACAO", length = 1) private String aceitaVendaFracao;
    @Column(name = "ENVIAINFTECNICANFE", length = 1) private String enviaInfTecnicaNfe;

    @Column(name = "TIPOCARGAPRODUTO", length = 1) private String tipoCargaProduto;
    @Column(name = "LOCALSEPARACAO", length = 2) private String localSeparacao;
    @Column(name = "NATUREZAPRODUTO", length = 2) private String naturezaProduto;
    @Column(name = "ENVIAECOMMERCE", length = 1) private String enviaEcommerce;
    @Column(name = "USAWMS", length = 1) private String usaWms;

    @Column(name = "STATUS_INTEGRACAO", nullable = false, length = 20)
    private String statusIntegracao = "PENDENTE";
    @Column(name = "CODPROD_ERP") private Long codProdErp;
    @Column(name = "DT_INTEGRACAO") private OffsetDateTime dataIntegracao;
    @Column(name = "MENSAGEM_ERRO", length = 1000) private String mensagemErro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_ID")
    private Usuario usuario;

    @Column(name = "DT_CRIACAO", nullable = false, updatable = false)
    private OffsetDateTime dataCriacao;

    @Column(name = "DT_ATUALIZACAO")
    private OffsetDateTime dataAtualizacao;

    @PrePersist
    void aoCriar() { this.dataCriacao = OffsetDateTime.now(); }

    @PreUpdate
    void aoAtualizar() { this.dataAtualizacao = OffsetDateTime.now(); }

    /** Campos sem os quais o WinThor recusa o cadastro. */
    public boolean isCompleto() {
        return camposFaltantes().isEmpty();
    }

    /**
     * Nome (como aparece na tela) de cada campo obrigatorio do WinThor ainda em branco.
     * Lista vazia = complemento pronto para a aprovacao e para a gravacao no ERP.
     */
    public java.util.List<String> camposFaltantes() {
        java.util.List<String> faltam = new java.util.ArrayList<>();
        exigirTexto(faltam, "Descricao", descricao);
        exigirNumero(faltam, "Marca", codMarca);
        exigirNumero(faltam, "Departamento", codEpto);
        exigirNumero(faltam, "Secao", codSec);
        exigirTexto(faltam, "Codigo de fabrica", codFab);
        exigirTexto(faltam, "NCM", classificacaoFiscal);
        exigirTexto(faltam, "CODNCMEX", codNcmEx);
        exigirTexto(faltam, "Embalagem", embalagem);
        exigirTexto(faltam, "Unidade", unidade);
        exigirNumero(faltam, "Qt. unitaria", qtUnit);
        exigirTexto(faltam, "Unidade master", unidadeMaster);
        exigirNumero(faltam, "Qt. por caixa", qtUnitCx);
        exigirNumero(faltam, "Multiplo", multiplo);
        exigirNumero(faltam, "Peso liquido", pesoLiq);
        exigirNumero(faltam, "Peso bruto", pesoBruto);
        exigirTexto(faltam, "Codigo de barras", codAuxiliar);
        exigirNumero(faltam, "GTIN (padrao 8/12/13/14)", gtinCodAuxiliar);
        exigirNumero(faltam, "Sugerir preco de venda", sugVenda);
        exigirNumero(faltam, "% comissao externa", pComExt1);
        exigirNumero(faltam, "% comissao interna", pComInt1);
        exigirNumero(faltam, "% comissao representante", pComRep1);
        exigirTexto(faltam, "Revenda", revenda);
        exigirNumero(faltam, "Tempo de reposicao (dias)", temRepos);
        exigirTexto(faltam, "Cesta basica", cestaBasicaLegis);
        exigirTexto(faltam, "Aceita venda fracao", aceitaVendaFracao);
        exigirTexto(faltam, "Tipo carga produto", tipoCargaProduto);
        exigirTexto(faltam, "Local de separacao", localSeparacao);
        exigirTexto(faltam, "Natureza do produto", naturezaProduto);
        exigirTexto(faltam, "Envia info tecnica NFe", enviaInfTecnicaNfe);
        exigirTexto(faltam, "Envia e-commerce", enviaEcommerce);
        exigirTexto(faltam, "Usa WMS", usaWms);
        return faltam;
    }

    private void exigirTexto(java.util.List<String> faltam, String rotulo, String valor) {
        if (!preenchido(valor)) faltam.add(rotulo);
    }

    private void exigirNumero(java.util.List<String> faltam, String rotulo, Object valor) {
        if (valor == null) faltam.add(rotulo);
    }

    private boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    public Long getProdutoId() { return produtoId; }
    public Produto getProduto() { return produto; }
    public void setProduto(Produto produto) { this.produto = produto; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String v) { this.descricao = v; }
    public Integer getCodMarca() { return codMarca; }
    public void setCodMarca(Integer v) { this.codMarca = v; }
    public Integer getCodEpto() { return codEpto; }
    public void setCodEpto(Integer v) { this.codEpto = v; }
    public Integer getCodSec() { return codSec; }
    public void setCodSec(Integer v) { this.codSec = v; }
    public String getCodFab() { return codFab; }
    public void setCodFab(String v) { this.codFab = v; }
    public String getCodNcmEx() { return codNcmEx; }
    public void setCodNcmEx(String v) { this.codNcmEx = v; }
    public String getClassificacaoFiscal() { return classificacaoFiscal; }
    public void setClassificacaoFiscal(String v) { this.classificacaoFiscal = v; }
    public String getEmbalagem() { return embalagem; }
    public void setEmbalagem(String v) { this.embalagem = v; }
    public String getUnidade() { return unidade; }
    public void setUnidade(String v) { this.unidade = v; }
    public BigDecimal getQtUnit() { return qtUnit; }
    public void setQtUnit(BigDecimal v) { this.qtUnit = v; }
    public String getUnidadeMaster() { return unidadeMaster; }
    public void setUnidadeMaster(String v) { this.unidadeMaster = v; }
    public BigDecimal getQtUnitCx() { return qtUnitCx; }
    public void setQtUnitCx(BigDecimal v) { this.qtUnitCx = v; }
    public BigDecimal getMultiplo() { return multiplo; }
    public void setMultiplo(BigDecimal v) { this.multiplo = v; }
    public BigDecimal getPesoLiq() { return pesoLiq; }
    public void setPesoLiq(BigDecimal v) { this.pesoLiq = v; }
    public BigDecimal getPesoBruto() { return pesoBruto; }
    public void setPesoBruto(BigDecimal v) { this.pesoBruto = v; }
    public String getCodAuxiliar() { return codAuxiliar; }
    public void setCodAuxiliar(String v) { this.codAuxiliar = v; }
    public Integer getGtinCodAuxiliar() { return gtinCodAuxiliar; }
    public void setGtinCodAuxiliar(Integer v) { this.gtinCodAuxiliar = v; }
    public Integer getSugVenda() { return sugVenda; }
    public void setSugVenda(Integer v) { this.sugVenda = v; }
    public BigDecimal getPComExt1() { return pComExt1; }
    public void setPComExt1(BigDecimal v) { this.pComExt1 = v; }
    public BigDecimal getPComInt1() { return pComInt1; }
    public void setPComInt1(BigDecimal v) { this.pComInt1 = v; }
    public BigDecimal getPComRep1() { return pComRep1; }
    public void setPComRep1(BigDecimal v) { this.pComRep1 = v; }
    public String getRevenda() { return revenda; }
    public void setRevenda(String v) { this.revenda = v; }
    public Integer getTemRepos() { return temRepos; }
    public void setTemRepos(Integer v) { this.temRepos = v; }
    public String getCestaBasicaLegis() { return cestaBasicaLegis; }
    public void setCestaBasicaLegis(String v) { this.cestaBasicaLegis = v; }
    public String getAceitaVendaFracao() { return aceitaVendaFracao; }
    public void setAceitaVendaFracao(String v) { this.aceitaVendaFracao = v; }
    public String getEnviaInfTecnicaNfe() { return enviaInfTecnicaNfe; }
    public void setEnviaInfTecnicaNfe(String v) { this.enviaInfTecnicaNfe = v; }
    public String getTipoCargaProduto() { return tipoCargaProduto; }
    public void setTipoCargaProduto(String v) { this.tipoCargaProduto = v; }
    public String getLocalSeparacao() { return localSeparacao; }
    public void setLocalSeparacao(String v) { this.localSeparacao = v; }
    public String getNaturezaProduto() { return naturezaProduto; }
    public void setNaturezaProduto(String v) { this.naturezaProduto = v; }
    public String getEnviaEcommerce() { return enviaEcommerce; }
    public void setEnviaEcommerce(String v) { this.enviaEcommerce = v; }
    public String getUsaWms() { return usaWms; }
    public void setUsaWms(String v) { this.usaWms = v; }
    public String getStatusIntegracao() { return statusIntegracao; }
    public void setStatusIntegracao(String v) { this.statusIntegracao = v; }
    public Long getCodProdErp() { return codProdErp; }
    public void setCodProdErp(Long v) { this.codProdErp = v; }
    public OffsetDateTime getDataIntegracao() { return dataIntegracao; }
    public void setDataIntegracao(OffsetDateTime v) { this.dataIntegracao = v; }
    public String getMensagemErro() { return mensagemErro; }
    public void setMensagemErro(String v) { this.mensagemErro = v; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario v) { this.usuario = v; }
    public OffsetDateTime getDataAtualizacao() { return dataAtualizacao; }
}
