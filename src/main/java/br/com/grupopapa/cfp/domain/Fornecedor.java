package br.com.grupopapa.cfp.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "CFP_FORNECEDOR")
public class Fornecedor {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqFornecedor")
    @SequenceGenerator(name = "seqFornecedor", sequenceName = "SEQ_CFP_FORNECEDOR", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CNPJ", nullable = false, length = 14, unique = true)
    private String cnpj;

    @Column(name = "RAZAO_SOCIAL", nullable = false, length = 150)
    private String razaoSocial;

    @Column(name = "NOME_FANTASIA", length = 150)
    private String nomeFantasia;

    @Column(name = "EMAIL", length = 150)
    private String email;

    @Column(name = "TELEFONE", length = 20)
    private String telefone;

    @Column(name = "COD_FORNEC_ERP")
    private Long codFornecedorErp;

    /** Filial de compra definida pelo administrador: o pedido de compra ja vem com ela, travada. */
    @Column(name = "COD_FILIAL_COMPRA", length = 2)
    private String codFilialCompra;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO", nullable = false, length = 20)
    private TipoFornecedor tipo = TipoFornecedor.GERAL;

    @Column(name = "ATIVO", nullable = false)
    private Boolean ativo = Boolean.TRUE;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }
    public String getRazaoSocial() { return razaoSocial; }
    public void setRazaoSocial(String razaoSocial) { this.razaoSocial = razaoSocial; }
    public String getNomeFantasia() { return nomeFantasia; }
    public void setNomeFantasia(String nomeFantasia) { this.nomeFantasia = nomeFantasia; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public Long getCodFornecedorErp() { return codFornecedorErp; }
    public void setCodFornecedorErp(Long codFornecedorErp) { this.codFornecedorErp = codFornecedorErp; }
    public String getCodFilialCompra() { return codFilialCompra; }
    public void setCodFilialCompra(String codFilialCompra) { this.codFilialCompra = codFilialCompra; }
    public TipoFornecedor getTipo() { return tipo == null ? TipoFornecedor.GERAL : tipo; }
    public void setTipo(TipoFornecedor tipo) { this.tipo = tipo; }
    public Boolean getAtivo() { return ativo; }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
    public OffsetDateTime getDataCriacao() { return dataCriacao; }
    public OffsetDateTime getDataAtualizacao() { return dataAtualizacao; }
}
