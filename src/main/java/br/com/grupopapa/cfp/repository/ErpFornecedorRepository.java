package br.com.grupopapa.cfp.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Acesso SOMENTE LEITURA a tabela de fornecedores do ERP (WinThor / PCFORNEC).
 * Nenhuma operacao de escrita e feita no ERP.
 */
@Repository
public class ErpFornecedorRepository {

    /** Registro cru vindo do ERP. */
    public record ErpFornecedor(Long codigo, String cgc, String razaoSocial,
                                String telefone, String email) { }

    private static final RowMapper<ErpFornecedor> MAPPER = (rs, i) -> new ErpFornecedor(
            rs.getObject("CODFORNEC") == null ? null : rs.getLong("CODFORNEC"),
            rs.getString("CGC"),
            rs.getString("FORNECEDOR"),
            rs.getString("TELFAB"),
            rs.getString("REP_EMAIL"));

    private final JdbcTemplate jdbcTemplate;
    private final String tabela;
    private final String tabelaProduto;
    private final int codeptoPiso;

    public ErpFornecedorRepository(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbcTemplate,
                                   @Value("${app.erp.tabela-fornecedor:PCFORNEC}") String tabela,
                                   @Value("${app.erp.tabela-produto:PCPRODUT}") String tabelaProduto,
                                   @Value("${app.erp.codepto-piso:13}") int codeptoPiso) {
        this.jdbcTemplate = jdbcTemplate;
        this.tabela = validarNome(tabela, "app.erp.tabela-fornecedor");
        this.tabelaProduto = validarNome(tabelaProduto, "app.erp.tabela-produto");
        this.codeptoPiso = codeptoPiso;
    }

    private static String validarNome(String nome, String propriedade) {
        if (!nome.matches("[A-Za-z0-9_.$]+")) {
            throw new IllegalStateException("Nome de tabela invalido em " + propriedade);
        }
        return nome;
    }

    /**
     * Dentre os codigos informados, devolve os que possuem produto no departamento de
     * piso/revestimento (CODEPTO 13 por padrao) - consulta somente leitura.
     */
    public Set<Long> codigosDePisoRevestimento(Collection<Long> codigos) {
        if (codigos == null || codigos.isEmpty()) {
            return Set.of();
        }
        List<Long> lista = codigos.stream().filter(java.util.Objects::nonNull).distinct().limit(900).toList();
        if (lista.isEmpty()) {
            return Set.of();
        }
        String marcadores = lista.stream().map(c -> "?").collect(Collectors.joining(","));
        String sql = "SELECT DISTINCT CODFORNEC FROM " + tabelaProduto
                   + " WHERE CODEPTO = ? AND CODFORNEC IN (" + marcadores + ")";

        Object[] params = new Object[lista.size() + 1];
        params[0] = codeptoPiso;
        for (int i = 0; i < lista.size(); i++) {
            params[i + 1] = lista.get(i);
        }
        return Set.copyOf(jdbcTemplate.queryForList(sql, Long.class, params));
    }

    /** true se o fornecedor tem pelo menos um produto no departamento de piso/revestimento. */
    public boolean ehPisoRevestimento(Long codFornec) {
        return !codigosDePisoRevestimento(List.of(codFornec)).isEmpty();
    }

    private static final String COLUNAS = "CODFORNEC, CGC, FORNECEDOR, TELFAB, REP_EMAIL";

    public List<ErpFornecedor> buscar(String termo, int limite) {
        String digitos = termo == null ? "" : termo.replaceAll("\\D", "");
        StringBuilder filtro = new StringBuilder();
        Object[] params;

        if (termo == null || termo.isBlank()) {
            params = new Object[]{limite};
        } else if (digitos.isEmpty()) {
            filtro.append(" WHERE UPPER(FORNECEDOR) LIKE UPPER('%' || ? || '%')");
            params = new Object[]{termo.trim(), limite};
        } else {
            filtro.append(" WHERE UPPER(FORNECEDOR) LIKE UPPER('%' || ? || '%')")
                  .append(" OR REPLACE(REPLACE(REPLACE(CGC, '.', ''), '/', ''), '-', '') LIKE '%' || ? || '%'")
                  .append(" OR TO_CHAR(CODFORNEC) = ?");
            params = new Object[]{termo.trim(), digitos, digitos, limite};
        }

        String sql = "SELECT * FROM (SELECT " + COLUNAS + " FROM " + tabela + filtro
                   + " ORDER BY FORNECEDOR) WHERE ROWNUM <= ?";
        return jdbcTemplate.query(sql, MAPPER, params);
    }

    public Optional<ErpFornecedor> buscarPorCodigo(Long codigo) {
        String sql = "SELECT " + COLUNAS + " FROM " + tabela + " WHERE CODFORNEC = ?";
        return jdbcTemplate.query(sql, MAPPER, codigo).stream().findFirst();
    }
}
