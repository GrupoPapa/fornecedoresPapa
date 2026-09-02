package br.com.grupopapa.cfp.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "CFP_PRODUTO_HIST")
public class ProdutoHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqProdutoHist")
    @SequenceGenerator(name = "seqProdutoHist", sequenceName = "SEQ_CFP_PRODUTO_HIST", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PRODUTO_ID")
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_ID")
    private Usuario usuario;

    @Column(name = "ACAO", nullable = false, length = 30)
    private String acao;

    @Column(name = "CAMPO", length = 50)
    private String campo;

    @Column(name = "VALOR_ANTERIOR", length = 500)
    private String valorAnterior;

    @Column(name = "VALOR_NOVO", length = 500)
    private String valorNovo;

    @Column(name = "DT_EVENTO", nullable = false, updatable = false)
    private OffsetDateTime dataEvento;

    @PrePersist
    void aoCriar() {
        this.dataEvento = OffsetDateTime.now();
    }

    public static ProdutoHistorico de(Produto produto, Usuario usuario, String acao,
                                      String campo, String valorAnterior, String valorNovo) {
        ProdutoHistorico h = new ProdutoHistorico();
        h.produto = produto;
        h.usuario = usuario;
        h.acao = acao;
        h.campo = campo;
        h.valorAnterior = valorAnterior;
        h.valorNovo = valorNovo;
        return h;
    }

    public Long getId() { return id; }
    public Produto getProduto() { return produto; }
    public Usuario getUsuario() { return usuario; }
    public String getAcao() { return acao; }
    public String getCampo() { return campo; }
    public String getValorAnterior() { return valorAnterior; }
    public String getValorNovo() { return valorNovo; }
    public OffsetDateTime getDataEvento() { return dataEvento; }
}
