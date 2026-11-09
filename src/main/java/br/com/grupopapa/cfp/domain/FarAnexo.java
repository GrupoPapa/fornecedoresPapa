package br.com.grupopapa.cfp.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "CFP_FAR_ANEXO")
public class FarAnexo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqFarAnexo")
    @SequenceGenerator(name = "seqFarAnexo", sequenceName = "SEQ_CFP_FAR_ANEXO", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "FAR_ID", nullable = false)
    private Long farId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false, length = 30)
    private TipoAnexoFar tipo = TipoAnexoFar.OUTRO;

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
    public Long getFarId() { return farId; }
    public void setFarId(Long farId) { this.farId = farId; }
    public TipoAnexoFar getTipo() { return tipo; }
    public void setTipo(TipoAnexoFar tipo) { this.tipo = tipo; }
    public String getNomeArquivo() { return nomeArquivo; }
    public void setNomeArquivo(String nomeArquivo) { this.nomeArquivo = nomeArquivo; }
    public String getTipoConteudo() { return tipoConteudo; }
    public void setTipoConteudo(String tipoConteudo) { this.tipoConteudo = tipoConteudo; }
    public Integer getTamanho() { return tamanho; }
    public void setTamanho(Integer tamanho) { this.tamanho = tamanho; }
    public byte[] getConteudo() { return conteudo; }
    public void setConteudo(byte[] conteudo) { this.conteudo = conteudo; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public OffsetDateTime getDataCriacao() { return dataCriacao; }
}
