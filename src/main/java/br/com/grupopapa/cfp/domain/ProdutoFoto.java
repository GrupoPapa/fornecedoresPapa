package br.com.grupopapa.cfp.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "CFP_PRODUTO_FOTO")
public class ProdutoFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqProdutoFoto")
    @SequenceGenerator(name = "seqProdutoFoto", sequenceName = "SEQ_CFP_PRODUTO_FOTO", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PRODUTO_ID")
    private Produto produto;

    @Column(name = "NOME_ARQUIVO", nullable = false, length = 200)
    private String nomeArquivo;

    @Column(name = "TIPO_CONTEUDO", nullable = false, length = 100)
    private String tipoConteudo;

    @Column(name = "TAMANHO", nullable = false)
    private Integer tamanho;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "CONTEUDO", nullable = false)
    private byte[] conteudo;

    @Column(name = "PRINCIPAL", nullable = false)
    private Boolean principal = Boolean.FALSE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_ID")
    private Usuario usuario;

    @Column(name = "DT_CRIACAO", nullable = false, updatable = false)
    private OffsetDateTime dataCriacao;

    @PrePersist
    void aoCriar() {
        this.dataCriacao = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Produto getProduto() { return produto; }
    public void setProduto(Produto produto) { this.produto = produto; }
    public String getNomeArquivo() { return nomeArquivo; }
    public void setNomeArquivo(String nomeArquivo) { this.nomeArquivo = nomeArquivo; }
    public String getTipoConteudo() { return tipoConteudo; }
    public void setTipoConteudo(String tipoConteudo) { this.tipoConteudo = tipoConteudo; }
    public Integer getTamanho() { return tamanho; }
    public void setTamanho(Integer tamanho) { this.tamanho = tamanho; }
    public byte[] getConteudo() { return conteudo; }
    public void setConteudo(byte[] conteudo) { this.conteudo = conteudo; }
    public Boolean getPrincipal() { return principal; }
    public void setPrincipal(Boolean principal) { this.principal = principal; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public OffsetDateTime getDataCriacao() { return dataCriacao; }
}
