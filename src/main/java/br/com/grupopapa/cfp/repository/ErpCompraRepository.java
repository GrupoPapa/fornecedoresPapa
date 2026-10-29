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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Consultas ao WinThor usadas no pedido de compra (somente leitura):
 * produtos do fornecedor (PCPRODUT), filiais (PCFILIAL), ultimas entradas (PCMOV)
 * e a situacao da sugestao gerada (PCSUGESTAOCOMPRAC/I).
 */
@Repository
public class ErpCompraRepository {

    private static final Logger log = LoggerFactory.getLogger(ErpCompraRepository.class);

    public record ProdutoWinthor(Long codProd, String descricao, String embalagem, String unidade,
                                 BigDecimal qtUnitCx, String codFab, String codAuxiliar,
                                 Long codFornec, boolean foraDeLinha) { }

    public record Filial(String codigo, String nome) { }

    public record UltimaCompra(LocalDate data, String numNota, String codFilial, Long codFornec,
                               String fornecedor, BigDecimal quantidade, BigDecimal precoUnitario) { }

    /** obs: a 220 grava ali o motivo quando nao consegue importar o item (STATUS continua P). */
    public record ItemSugestao(Long codProd, String status, Long numPed, BigDecimal qtPedido, String obs) { }

    public record SituacaoSugestao(boolean encontrada, boolean importada, List<ItemSugestao> itens) { }

    private final JdbcTemplate jdbc;
    private final String tabelaProduto;
    private final String tabelaMovimento;
    private final String tabelaSugestaoCab;
    private final String tabelaSugestaoItem;
    private final List<String> filiaisPermitidas;

    public ErpCompraRepository(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc,
                               @Value("${app.erp.tabela-produto:PCPRODUT}") String tabelaProduto,
                               @Value("${app.erp.compra.tabela-movimento:PCMOV}") String tabelaMovimento,
                               @Value("${app.erp.compra.tabela-sugestao:PCSUGESTAOCOMPRAC}") String cab,
                               @Value("${app.erp.compra.tabela-sugestao-item:PCSUGESTAOCOMPRAI}") String item,
                               @Value("${app.erp.compra.filiais:}") String filiais) {
        this.jdbc = jdbc;
        this.tabelaProduto = validar(tabelaProduto);
        this.tabelaMovimento = validar(tabelaMovimento);
        this.tabelaSugestaoCab = validar(cab);
        this.tabelaSugestaoItem = validar(item);
        this.filiaisPermitidas = filiais == null ? List.of()
                : Arrays.stream(filiais.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    static String validar(String nome) {
        if (nome == null || !nome.matches("[A-Za-z0-9_.$]+")) {
            throw new IllegalStateException("Nome de tabela invalido: " + nome);
        }
        return nome;
    }

    // ------------------------------------------------------------- produtos

    private static final RowMapper<ProdutoWinthor> PRODUTO = (rs, i) -> new ProdutoWinthor(
            rs.getLong("CODPROD"),
            rs.getString("DESCRICAO"),
            rs.getString("EMBALAGEM"),
            rs.getString("UNIDADE"),
            rs.getBigDecimal("QTUNITCX"),
            rs.getString("CODFAB"),
            rs.getString("CODAUXILIAR"),
            rs.getObject("CODFORNEC") == null ? null : rs.getLong("CODFORNEC"),
            "FL".equals(rs.getString("OBS2")));

    private static final String COLUNAS_PRODUTO =
            "CODPROD, DESCRICAO, EMBALAGEM, UNIDADE, QTUNITCX, CODFAB, "
          + "TO_CHAR(CODAUXILIAR) CODAUXILIAR, CODFORNEC, OBS2";

    /** Produtos ativos do fornecedor (fora de linha e excluidos ficam de fora). */
    public List<ProdutoWinthor> produtosDoFornecedor(Long codFornec) {
        if (codFornec == null) {
            return List.of();
        }
        String base = "SELECT " + COLUNAS_PRODUTO + " FROM " + tabelaProduto
                + " WHERE CODFORNEC = ? AND NVL(OBS2, ' ') <> 'FL'";
        try {
            return jdbc.query(base + " AND DTEXCLUSAO IS NULL ORDER BY DESCRICAO", PRODUTO, codFornec);
        } catch (DataAccessException e) {
            // base sem a coluna DTEXCLUSAO: tenta sem o filtro antes de desistir
            log.warn("Consulta de produtos com DTEXCLUSAO falhou ({}); tentando sem o filtro.",
                    e.getMostSpecificCause().getMessage());
            return jdbc.query(base + " ORDER BY DESCRICAO", PRODUTO, codFornec);
        }
    }

    /** Busca os produtos pelo codigo, independente do fornecedor (para conferir divergencias). */
    public Map<Long, ProdutoWinthor> produtosPorCodigo(Collection<Long> codigos) {
        List<Long> lista = codigos.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, ProdutoWinthor> resultado = new HashMap<>();
        for (int i = 0; i < lista.size(); i += 500) {
            List<Long> parte = lista.subList(i, Math.min(i + 500, lista.size()));
            String marcadores = parte.stream().map(c -> "?").collect(Collectors.joining(","));
            String sql = "SELECT " + COLUNAS_PRODUTO + " FROM " + tabelaProduto
                    + " WHERE CODPROD IN (" + marcadores + ")";
            for (ProdutoWinthor p : jdbc.query(sql, PRODUTO, parte.toArray())) {
                resultado.put(p.codProd(), p);
            }
        }
        return resultado;
    }

    // ------------------------------------------------- tabela de preco (220)

    /**
     * Mesma conferencia que a rotina 220 faz ao importar ("Nao existe tabela de preco
     * (Rotina 203 Editar/Salvar)"): o produto precisa de PCTABPR com tributacao (CODST) na
     * regiao padrao da filial. Com PCCONSUM.USATRIBUTACAOPORUF = 'S' a tributacao vem da
     * PCTABTRIB da UF da regiao. Devolve os CODPROD que a 220 vai recusar.
     */
    public Set<Long> semTabelaPreco(Collection<Long> codigos, String codFilial) {
        List<Long> lista = codigos.stream().filter(Objects::nonNull).distinct().toList();
        Set<Long> resultado = new HashSet<>();
        if (lista.isEmpty() || codFilial == null) {
            return resultado;
        }
        for (int i = 0; i < lista.size(); i += 500) {
            List<Long> parte = lista.subList(i, Math.min(i + 500, lista.size()));
            String marcadores = parte.stream().map(c -> "?").collect(Collectors.joining(","));
            String sql = "SELECT P.CODPROD"
                + "  FROM " + tabelaProduto + " P, PCFILIAL FI, PCCONSUM C, PCEST E"
                + " WHERE FI.CODIGO = ?"
                + "   AND P.CODPROD IN (" + marcadores + ")"
                + "   AND E.CODPROD(+) = P.CODPROD"
                + "   AND E.CODFILIAL(+) = FI.CODIGO"
                + "   AND 1 = CASE WHEN NVL(C.USATRIBUTACAOPORUF, 'N') = 'N' THEN"
                + "         CASE WHEN EXISTS (SELECT 0 FROM PCTABPR T, PCTRIBUT TR"
                + "                            WHERE T.NUMREGIAO = NVL(FI.NUMREGIAOPADRAO, C.NUMREGIAOPADRAO)"
                + "                              AND T.CODPROD = P.CODPROD"
                + "                              AND T.CODST = TR.CODST) THEN 0 ELSE 1 END"
                + "       ELSE"
                + "         CASE WHEN EXISTS (SELECT 0 FROM PCTABPR T, PCTABTRIB TT, PCTRIBUT TR, PCREGIAO R"
                + "                            WHERE T.NUMREGIAO = R.NUMREGIAO"
                + "                              AND T.NUMREGIAO = NVL(FI.NUMREGIAOPADRAO, C.NUMREGIAOPADRAO)"
                + "                              AND T.CODPROD = P.CODPROD"
                + "                              AND TT.CODPROD = P.CODPROD"
                + "                              AND TT.UFDESTINO = R.UF"
                + "                              AND TT.CODST = TR.CODST"
                + "                              AND TT.CODFILIALNF = DECODE(NVL(R.CODFILIAL, '99'), '99', E.CODFILIAL, R.CODFILIAL))"
                + "              THEN 0 ELSE 1 END"
                + "       END";
            Object[] params = new Object[parte.size() + 1];
            params[0] = codFilial;
            for (int k = 0; k < parte.size(); k++) {
                params[k + 1] = parte.get(k);
            }
            resultado.addAll(jdbc.queryForList(sql, Long.class, params));
        }
        return resultado;
    }

    // -------------------------------------------------------------- filiais

    /**
     * Filiais que podem receber o pedido. Se app.erp.compra.filiais estiver preenchido,
     * so essas aparecem. Se o WinThor nao responder, devolve a lista configurada.
     */
    public List<Filial> filiais() {
        List<Filial> todas;
        try {
            todas = jdbc.query("SELECT CODIGO, NVL(FANTASIA, RAZAOSOCIAL) NOME FROM PCFILIAL "
                            + "WHERE CODIGO <> '99' AND DTEXCLUSAO IS NULL ORDER BY CODIGO",
                    (rs, i) -> new Filial(rs.getString(1), rs.getString(2)));
        } catch (DataAccessException e) {
            log.warn("Nao foi possivel ler as filiais com nome ({}); usando so o codigo.",
                    e.getMostSpecificCause().getMessage());
            try {
                todas = jdbc.query("SELECT CODIGO FROM PCFILIAL WHERE CODIGO <> '99' ORDER BY CODIGO",
                        (rs, i) -> new Filial(rs.getString(1), "Filial " + rs.getString(1)));
            } catch (DataAccessException e2) {
                todas = filiaisPermitidas.stream().map(c -> new Filial(c, "Filial " + c)).toList();
            }
        }
        if (filiaisPermitidas.isEmpty()) {
            return todas;
        }
        return todas.stream().filter(f -> filiaisPermitidas.contains(f.codigo())).toList();
    }

    public List<String> filiaisPermitidas() {
        return filiaisPermitidas;
    }

    // ------------------------------------------------------- ultimos precos

    /** Ultimas entradas (CODOPER = 'E') do produto, da mais recente para a mais antiga. */
    public List<UltimaCompra> ultimasCompras(Long codProd, int limite) {
        String sql = "SELECT * FROM ("
                + " SELECT M.DTMOV, M.NUMNOTA, M.CODFILIAL, M.CODFORNEC, F.FORNECEDOR, M.QT, M.PUNIT"
                + "   FROM " + tabelaMovimento + " M, PCFORNEC F"
                + "  WHERE M.CODFORNEC = F.CODFORNEC(+)"
                + "    AND M.CODPROD = ?"
                + "    AND M.CODOPER = 'E'"
                + "    AND M.DTCANCEL IS NULL"
                + "    AND NVL(M.PUNIT, 0) > 0"
                + "  ORDER BY M.DTMOV DESC, M.NUMNOTA DESC"
                + ") WHERE ROWNUM <= ?";
        return jdbc.query(sql, this::ultimaCompra, codProd, limite);
    }

    private UltimaCompra ultimaCompra(ResultSet rs, int i) throws SQLException {
        java.sql.Date data = rs.getDate("DTMOV");
        return new UltimaCompra(
                data == null ? null : data.toLocalDate(),
                rs.getString("NUMNOTA"),
                rs.getString("CODFILIAL"),
                rs.getObject("CODFORNEC") == null ? null : rs.getLong("CODFORNEC"),
                rs.getString("FORNECEDOR"),
                rs.getBigDecimal("QT"),
                rs.getBigDecimal("PUNIT"));
    }

    // --------------------------------------------------- situacao na 220

    public SituacaoSugestao situacaoSugestao(Long numSugestao) {
        List<String> cab = jdbc.queryForList("SELECT NVL(IMPORTACAO, 'N') FROM " + tabelaSugestaoCab
                + " WHERE NUMSUGESTAO = ?", String.class, numSugestao);
        if (cab.isEmpty()) {
            return new SituacaoSugestao(false, false, List.of());
        }
        List<ItemSugestao> itens = jdbc.query("SELECT CODPROD, NVL(STATUS, 'P') STATUS, NUMPED, QTPEDIDO, OBS FROM "
                        + tabelaSugestaoItem + " WHERE NUMSUGESTAO = ? ORDER BY CODPROD",
                (rs, i) -> new ItemSugestao(rs.getLong("CODPROD"), rs.getString("STATUS"),
                        rs.getObject("NUMPED") == null ? null : rs.getLong("NUMPED"),
                        rs.getBigDecimal("QTPEDIDO"), rs.getString("OBS")),
                numSugestao);
        // a 220 nao marca IMPORTACAO no cabecalho: ao gerar o pedido ela poe STATUS = 'F'
        // e o NUMPED em cada item. Importada = todos os itens ja tem pedido.
        boolean importada = "S".equals(cab.get(0))
                || (!itens.isEmpty() && itens.stream().allMatch(i -> i.numPed() != null));
        return new SituacaoSugestao(true, importada, itens);
    }
}
