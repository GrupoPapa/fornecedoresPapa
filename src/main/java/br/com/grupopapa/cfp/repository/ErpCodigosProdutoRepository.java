package br.com.grupopapa.cfp.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Conferencia de EAN, DUN e codigo de fabrica no cadastro do WinThor (PCPRODUT), para o
 * fornecedor nao cadastrar no portal um codigo que ja existe la. Somente leitura.
 *   CODAUXILIAR  = EAN da unidade      CODAUXILIAR2 = DUN da caixa master
 */
@Repository
public class ErpCodigosProdutoRepository {

    /** Produto do WinThor que ja usa o codigo. campo: EAN, DUN ou COD. FABRICA. */
    public record ProdutoComCodigo(Long codProd, String descricao, Long codFornec, String fornecedor, String campo) { }

    private final JdbcTemplate jdbc;
    private final String tabelaProduto;
    private final String tabelaFornecedor;

    public ErpCodigosProdutoRepository(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc,
                                       @Value("${app.erp.tabela-produto:PCPRODUT}") String tabelaProduto,
                                       @Value("${app.erp.tabela-fornecedor:PCFORNEC}") String tabelaFornecedor) {
        for (String t : List.of(tabelaProduto, tabelaFornecedor)) {
            if (!t.matches("[A-Za-z0-9_.$#]+")) {
                throw new IllegalStateException("Nome de tabela invalido: " + t);
            }
        }
        this.jdbc = jdbc;
        this.tabelaProduto = tabelaProduto;
        this.tabelaFornecedor = tabelaFornecedor;
    }

    /** Produtos ativos que tem o codigo de barras como EAN ou como DUN (ignora o proprio CODPROD). */
    public List<ProdutoComCodigo> comCodigoDeBarras(long codigo, Long ignorarCodProd) {
        return jdbc.query(
                "SELECT P.CODPROD, P.DESCRICAO, P.CODFORNEC, F.FORNECEDOR,"
                        + "       CASE WHEN P.CODAUXILIAR = ? THEN 'EAN' ELSE 'DUN' END CAMPO"
                        + "  FROM " + tabelaProduto + " P"
                        + "  LEFT JOIN " + tabelaFornecedor + " F ON F.CODFORNEC = P.CODFORNEC"
                        + " WHERE (P.CODAUXILIAR = ? OR P.CODAUXILIAR2 = ?)"
                        + "   AND P.DTEXCLUSAO IS NULL"
                        + (ignorarCodProd != null ? "   AND P.CODPROD <> ?" : "")
                        + "   AND ROWNUM <= 5",
                (rs, i) -> new ProdutoComCodigo(rs.getLong("CODPROD"), rs.getString("DESCRICAO"),
                        rs.getObject("CODFORNEC") == null ? null : rs.getLong("CODFORNEC"),
                        rs.getString("FORNECEDOR"), rs.getString("CAMPO")),
                ignorarCodProd != null
                        ? new Object[]{codigo, codigo, codigo, ignorarCodProd}
                        : new Object[]{codigo, codigo, codigo});
    }

    /** Produtos ativos do mesmo fornecedor com o codigo de fabrica (sem diferenciar maiusculas/espacos). */
    public List<ProdutoComCodigo> comCodigoFabrica(long codFornec, String codFab, Long ignorarCodProd) {
        return jdbc.query(
                "SELECT P.CODPROD, P.DESCRICAO, P.CODFORNEC, NULL FORNECEDOR, 'COD. FABRICA' CAMPO"
                        + "  FROM " + tabelaProduto + " P"
                        + " WHERE P.CODFORNEC = ?"
                        + "   AND UPPER(TRIM(P.CODFAB)) = UPPER(TRIM(?))"
                        + "   AND P.DTEXCLUSAO IS NULL"
                        + (ignorarCodProd != null ? "   AND P.CODPROD <> ?" : "")
                        + "   AND ROWNUM <= 5",
                (rs, i) -> new ProdutoComCodigo(rs.getLong("CODPROD"), rs.getString("DESCRICAO"),
                        rs.getLong("CODFORNEC"), null, rs.getString("CAMPO")),
                ignorarCodProd != null
                        ? new Object[]{codFornec, codFab, ignorarCodProd}
                        : new Object[]{codFornec, codFab});
    }
}
