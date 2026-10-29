package br.com.grupopapa.cfp.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "CFP_PEDIDO_COMPRA_HIST")
public class PedidoCompraHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqPedidoCompraHist")
    @SequenceGenerator(name = "seqPedidoCompraHist", sequenceName = "SEQ_CFP_PEDIDO_COMPRA_HIST", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "PEDIDO_COMPRA_ID", nullable = false)
    private Long pedidoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USUARIO_ID")
    private Usuario usuario;

    @Column(name = "EVENTO", nullable = false, length = 30)
    private String evento;

    @Column(name = "DESCRICAO", length = 2000)
    private String descricao;

    @Column(name = "DT_EVENTO", nullable = false, updatable = false)
    private OffsetDateTime dataEvento;

    @PrePersist
    void aoCriar() {
        this.dataEvento = OffsetDateTime.now();
    }

    public static PedidoCompraHistorico de(Long pedidoId, Usuario usuario, String evento, String descricao) {
        PedidoCompraHistorico h = new PedidoCompraHistorico();
        h.pedidoId = pedidoId;
        h.usuario = usuario;
        h.evento = evento;
        h.descricao = descricao == null ? null
                : (descricao.length() > 2000 ? descricao.substring(0, 2000) : descricao);
        return h;
    }

    public Long getId() { return id; }
    public Long getPedidoId() { return pedidoId; }
    public Usuario getUsuario() { return usuario; }
    public String getEvento() { return evento; }
    public String getDescricao() { return descricao; }
    public OffsetDateTime getDataEvento() { return dataEvento; }
}
