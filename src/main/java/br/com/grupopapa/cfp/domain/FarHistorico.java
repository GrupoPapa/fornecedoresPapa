package br.com.grupopapa.cfp.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "CFP_FAR_HIST")
public class FarHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqFarHist")
    @SequenceGenerator(name = "seqFarHist", sequenceName = "SEQ_CFP_FAR_HIST", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "FAR_ID", nullable = false)
    private Long farId;

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

    public static FarHistorico de(Long farId, Usuario usuario, String evento, String descricao) {
        FarHistorico h = new FarHistorico();
        h.farId = farId;
        h.usuario = usuario;
        h.evento = evento;
        h.descricao = descricao == null ? null
                : (descricao.length() > 2000 ? descricao.substring(0, 2000) : descricao);
        return h;
    }

    public Long getId() { return id; }
    public Long getFarId() { return farId; }
    public Usuario getUsuario() { return usuario; }
    public String getEvento() { return evento; }
    public String getDescricao() { return descricao; }
    public OffsetDateTime getDataEvento() { return dataEvento; }
}
