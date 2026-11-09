package br.com.grupopapa.cfp.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Conferencia da nota fiscal de compra no WinThor (somente leitura).
 * A nota e de compra quando tem entrada na PCMOV com CODOPER = 'E' para o fornecedor.
 */
@Repository
public class ErpNotaCompraRepository {

    public record NotaCompra(Long numNota, LocalDate dataEntrada, String codFilial,
                             int qtdItens, BigDecimal valorTotal, List<Long> produtos) { }

    private final JdbcTemplate jdbc;
    private final String tabelaMovimento;

    public ErpNotaCompraRepository(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc,
                                   @Value("${app.erp.compra.tabela-movimento:PCMOV}") String tabelaMovimento) {
        this.jdbc = jdbc;
        if (!tabelaMovimento.matches("[A-Za-z0-9_.$#]+")) {
            throw new IllegalStateException("Nome de tabela invalido: " + tabelaMovimento);
        }
        this.tabelaMovimento = tabelaMovimento;
    }

    /** Entrada por compra da nota para o fornecedor; vazio quando nao existe. */
    public Optional<NotaCompra> notaDeCompra(long codFornec, long numNota) {
        List<Map<String, Object>> linhas = jdbc.queryForList(
                "SELECT M.CODPROD, M.DTMOV, M.CODFILIAL, M.QT, M.PUNIT"
                        + "  FROM " + tabelaMovimento + " M"
                        + " WHERE M.NUMNOTA = ?"
                        + "   AND M.CODFORNEC = ?"
                        + "   AND M.CODOPER = 'E'"
                        + "   AND M.DTCANCEL IS NULL",
                numNota, codFornec);
        if (linhas.isEmpty()) {
            return Optional.empty();
        }
        LocalDate data = null;
        String filial = null;
        BigDecimal total = BigDecimal.ZERO;
        Set<Long> produtos = new LinkedHashSet<>();
        for (Map<String, Object> l : linhas) {
            Object dt = l.get("DTMOV");
            LocalDate d = dt instanceof java.sql.Timestamp ts ? ts.toLocalDateTime().toLocalDate()
                    : dt instanceof java.sql.Date sd ? sd.toLocalDate() : null;
            if (d != null && (data == null || d.isAfter(data))) data = d;
            if (filial == null && l.get("CODFILIAL") != null) filial = l.get("CODFILIAL").toString();
            if (l.get("CODPROD") instanceof Number n) produtos.add(n.longValue());
            if (l.get("QT") instanceof Number qt && l.get("PUNIT") instanceof Number pu) {
                total = total.add(new BigDecimal(qt.toString()).multiply(new BigDecimal(pu.toString())));
            }
        }
        return Optional.of(new NotaCompra(numNota, data, filial, produtos.size(),
                total.setScale(2, java.math.RoundingMode.HALF_UP), new ArrayList<>(produtos)));
    }
}
