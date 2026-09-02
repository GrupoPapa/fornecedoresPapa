package br.com.grupopapa.cfp.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "CFP_USUARIO")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqUsuario")
    @SequenceGenerator(name = "seqUsuario", sequenceName = "SEQ_CFP_USUARIO", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "FORNECEDOR_ID")
    private Fornecedor fornecedor;

    @Column(name = "NOME", nullable = false, length = 150)
    private String nome;

    @Column(name = "EMAIL", nullable = false, length = 150, unique = true)
    private String email;

    @Column(name = "SENHA", nullable = false, length = 100)
    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(name = "PERFIL", nullable = false, length = 20)
    private Perfil perfil;

    @Column(name = "ATIVO", nullable = false)
    private Boolean ativo = Boolean.TRUE;

    @Column(name = "TROCAR_SENHA", nullable = false)
    private Boolean trocarSenha = Boolean.TRUE;

    @Column(name = "DT_ULTIMO_ACESSO")
    private OffsetDateTime dataUltimoAcesso;

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

    public boolean isAdmin() { return Perfil.ADMIN.equals(perfil); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Fornecedor getFornecedor() { return fornecedor; }
    public void setFornecedor(Fornecedor fornecedor) { this.fornecedor = fornecedor; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
    public Perfil getPerfil() { return perfil; }
    public void setPerfil(Perfil perfil) { this.perfil = perfil; }
    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
    public Boolean getTrocarSenha() { return trocarSenha; }
    public void setTrocarSenha(Boolean trocarSenha) { this.trocarSenha = trocarSenha; }
    public OffsetDateTime getDataUltimoAcesso() { return dataUltimoAcesso; }
    public void setDataUltimoAcesso(OffsetDateTime dataUltimoAcesso) { this.dataUltimoAcesso = dataUltimoAcesso; }
    public OffsetDateTime getDataCriacao() { return dataCriacao; }
}
