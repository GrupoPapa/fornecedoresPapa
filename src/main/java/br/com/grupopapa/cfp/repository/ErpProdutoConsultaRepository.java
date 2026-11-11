package br.com.grupopapa.cfp.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Produtos do fornecedor ja cadastrados no WinThor (PCPRODUT), com os dados que o
 * fornecedor confere na tela "Todos os produtos". Somente leitura.
 */
@Repository
public class ErpProdutoConsultaRepository {

    private static final Logger log = LoggerFactory.getLogger(ErpProdutoConsultaRepository.class);

    public record ProdutoCadastrado(Long codProd, String descricao, String codFab, String ean, String ncm,
                                    String embalagem, String unidade, BigDecimal qtUnitCx,
                                    BigDecimal pesoLiquido, BigDecimal pesoBruto, LocalDate dataCadastro,
                                    boolean foraDeLinha) { }

    private final JdbcTemplate jdbc;
    private final String tabelaProduto;

    public ErpProdutoConsultaRepository(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc,
                                        @Value("${app.erp.tabela-produto:PCPRODUT}") String tabelaProduto) {
        if (!tabelaProduto.matches("[A-Za-z0-9_.$#]+")) {
            throw new IllegalStateException("Nome de tabela invalido: " + tabelaProduto);
        }
        this.jdbc = jdbc;
        this.tabelaProduto = tabelaProduto;
    }

    private static final RowMapper<ProdutoCadastrado> MAPA = (rs, i) -> new ProdutoCadastrado(
            rs.getLong("CODPROD"),
            rs.getString("DESCRICAO"),
            rs.getString("CODFAB"),
            rs.getString("EAN"),
            rs.getString("NCM"),
            rs.getString("EMBALAGEM"),
            rs.getString("UNIDADE"),
            rs.getBigDecimal("QTUNITCX"),
            rs.getBigDecimal("PESOLIQ"),
            rs.getBigDecimal("PESOBRUTO"),
            data(rs, "DTCADASTRO"),
            "FL".equals(rs.getString("OBS2")));

    private static LocalDate data(ResultSet rs, String coluna) throws SQLException {
        java.sql.Timestamp t = rs.getTimestamp(coluna);
        return t == null ? null : t.toLocalDateTime().toLocalDate();
    }

    /**
     * Todos os produtos do fornecedor, inclusive os fora de linha (marcados). Excluidos ficam de fora.
     * O NCM fica em NBM na maioria das bases; se a coluna nao existir, usa CLASSIFICFISCAL.
     */
    public List<ProdutoCadastrado> doFornecedor(Long codFornec) {
        if (codFornec == null) {
            return List.of();
        }
        for (String ncm : List.of("NBM", "CLASSIFICFISCAL", "NULL")) {
            for (boolean filtrarExcluidos : List.of(true, false)) {
                String sql = "SELECT CODPROD, DESCRICAO, CODFAB, TO_CHAR(CODAUXILIAR) EAN, TO_CHAR(" + ncm + ") NCM,"
                        + " EMBALAGEM, UNIDADE, QTUNITCX, PESOLIQ, PESOBRUTO, DTCADASTRO, OBS2"
                        + "  FROM " + tabelaProduto
                        + " WHERE CODFORNEC = ?"
                        + (filtrarExcluidos ? " AND DTEXCLUSAO IS NULL" : "")
                        + " ORDER BY DESCRICAO";
                try {
                    return jdbc.query(sql, MAPA, codFornec);
                } catch (DataAccessException e) {
                    log.warn("Consulta de produtos do fornecedor {} falhou (NCM={}, filtro exclusao={}): {}",
                            codFornec, ncm, filtrarExcluidos, e.getMostSpecificCause().getMessage());
                }
            }
        }
        throw new IllegalStateException("Nao foi possivel consultar os produtos no WinThor.");
    }
}
