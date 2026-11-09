package br.com.grupopapa.cfp.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Motivo de avaria pre-cadastrado pela equipe interna. */
@Entity
@Table(name = "CFP_FAR_MOTIVO")
public class FarMotivo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqFarMotivo")
    @SequenceGenerator(name = "seqFarMotivo", sequenceName = "SEQ_CFP_FAR_MOTIVO", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DESCRICAO", nullable = false, length = 100)
    private String descricao;

    @Column(name = "ATIVO", nullable = false)
    private Boolean ativo = Boolean.TRUE;

    @Column(name = "DT_CRIACAO", nullable = false, updatable = false)
    private OffsetDateTime dataCriacao;

    @PrePersist
    void aoCriar() {
        this.dataCriacao = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
    public OffsetDateTime getDataCriacao() { return dataCriacao; }
}
