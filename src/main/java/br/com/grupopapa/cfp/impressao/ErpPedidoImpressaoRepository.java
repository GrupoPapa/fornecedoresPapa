package br.com.grupopapa.cfp.impressao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Le no WinThor um pedido de compra ja gerado (rotina 220) para imprimir no layout da 210.
 *
 * Os nomes de algumas colunas variam entre versoes do WinThor, por isso as tabelas sao lidas
 * com SELECT * e cada campo procura uma lista de nomes possiveis (o primeiro preenchido vence).
 * Se algum campo sair vazio, GET /api/admin/pedidos-compra/winthor/{numPed}/campos mostra as
 * colunas reais para ajustar as listas abaixo.
 */
@Repository
public class ErpPedidoImpressaoRepository {

    private static final Logger log = LoggerFactory.getLogger(ErpPedidoImpressaoRepository.class);
    private static final Pattern ORIGEM = Pattern.compile("^\\s*\\d{3}\\s*-.*");

    private final JdbcTemplate jdbc;
    private final List<String> rodape;

    public ErpPedidoImpressaoRepository(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc,
                                        @Value("${app.impressao-pedido.rodape:"
                                                + "*** Favor informar nas observacoes da nota fiscal o numero do pedido de compra ***|"
                                                + "Copia pedido: papa.compras@hotmail.com|"
                                                + "XML e boletos: nfexmlpapa@gmaill.com}") String rodape) {
        this.jdbc = jdbc;
        this.rodape = Arrays.stream(rodape.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public Optional<PedidoWinthorImpressao> buscar(Long numPed) {
        List<Map<String, Object>> cabs = jdbc.queryForList("SELECT * FROM PCPEDIDO WHERE NUMPED = ?", numPed);
        if (cabs.isEmpty()) {
            return Optional.empty();
        }
        Linha c = new Linha(cabs.get(0));

        // ----- empresa (filial do pedido)
        String codFilial = c.s("CODFILIAL");
        Linha fil = umaLinha("SELECT * FROM PCFILIAL WHERE CODIGO = ?", codFilial);
        PedidoWinthorImpressao.Empresa empresa = new PedidoWinthorImpressao.Empresa(codFilial,
                fil.s("RAZAOSOCIAL", "FANTASIA"), fil.s("CGC", "CNPJ"), fil.s("IE", "INSCRICAOESTADUAL"),
                fil.s("ENDERECO", "ENDER"), fil.s("CIDADE", "MUNICIPIO"), fil.s("UF", "ESTADO"), fil.s("CEP"),
                fil.s("SUFRAMA", "INSCSUFRAMA", "CODSUFRAMA"));

        // ----- fornecedor
        Long codFornec = c.l("CODFORNEC");
        Linha f = umaLinha("SELECT * FROM PCFORNEC WHERE CODFORNEC = ?", codFornec);
        PedidoWinthorImpressao.Fornecedor fornecedor = new PedidoWinthorImpressao.Fornecedor(codFornec,
                f.s("FORNECEDOR"), f.s("CGC", "CNPJ"), f.s("ENDER", "ENDERECO"), f.s("BAIRRO"),
                f.s("CIDADE"), f.s("ESTADO", "UF"), f.s("CEP"),
                f.s("TELFAB", "TELEFONE", "TELREP", "FONE"));

        Long codTransp = c.l("CODTRANSP", "CODFORNECFRETE", "CODTRANSPORTADORA");
        String transportador = codTransp == null || codTransp == 0 ? null
                : umaLinha("SELECT * FROM PCFORNEC WHERE CODFORNEC = ?", codTransp).s("FORNECEDOR");

        // ----- itens
        List<Map<String, Object>> linhas = jdbc.queryForList("SELECT I.*, P.DESCRICAO PROD_DESCRICAO,"
                + " P.EMBALAGEM PROD_EMBALAGEM, P.UNIDADE PROD_UNIDADE, P.CODFAB PROD_CODFAB"
                + " FROM PCITEM I, PCPRODUT P WHERE P.CODPROD(+) = I.CODPROD AND I.NUMPED = ?", numPed);
        List<Linha> brutas = new ArrayList<>(linhas.stream().map(Linha::new).toList());
        brutas.sort(Comparator.comparing((Linha l) -> Optional.ofNullable(l.n("NUMSEQ", "SEQ")).orElse(BigDecimal.ZERO))
                .thenComparing(l -> Optional.ofNullable(l.s("PROD_DESCRICAO")).orElse("")));
        List<PedidoWinthorImpressao.Item> itens = new ArrayList<>();
        BigDecimal somaItens = BigDecimal.ZERO;
        for (Linha i : brutas) {
            BigDecimal qt = i.n("QTPEDIDA", "QT");
            BigDecimal preco = i.n("PLIQUIDO", "PCOMPRA", "PRECO");
            BigDecimal total = qt == null || preco == null ? null : qt.multiply(preco).setScale(2, RoundingMode.HALF_UP);
            if (total != null) somaItens = somaItens.add(total);
            itens.add(new PedidoWinthorImpressao.Item(i.l("CODPROD"), i.s("PROD_CODFAB", "CODFAB"),
                    i.s("PROD_DESCRICAO", "DESCRICAO"), i.s("PROD_EMBALAGEM", "EMBALAGEM"),
                    i.s("PROD_UNIDADE", "UNIDADE"), qt, Optional.ofNullable(i.n("VLIPI")).orElse(BigDecimal.ZERO),
                    preco, total));
        }

        // ----- condicao de pagamento: PRAZO1..PRAZO12 preenchidos
        List<String> prazos = new ArrayList<>();
        for (int n = 1; n <= 12; n++) {
            BigDecimal p = c.n("PRAZO" + n);
            if (p != null && p.signum() > 0) prazos.add(p.stripTrailingZeros().toPlainString());
        }

        // ----- observacoes: OBS* ; linha "220 - ..." vai para a origem
        List<String> obs = new ArrayList<>();
        String origem = null;
        for (String col : c.colunas()) {
            if (!col.startsWith("OBS") && !col.equals("HISTORICO")) continue;
            String t = c.s(col);
            if (t == null) continue;
            if (origem == null && ORIGEM.matcher(t).matches()) origem = t;
            else obs.add(t);
        }

        BigDecimal totalLiquido = Optional.ofNullable(c.n("VLTOTAL", "VLTOTPED", "VLTOTALPED")).orElse(somaItens);
        BigDecimal desconto = Optional.ofNullable(c.n("VLDESCSUFRAMA", "VLSUFRAMA")).orElse(BigDecimal.ZERO);
        BigDecimal totalBruto = Optional.ofNullable(c.n("VLBRUTO", "VLTOTALBRUTO")).orElse(totalLiquido.add(desconto));

        String tipoEmb = c.s("TIPOEMBALAGEMPEDIDO", "TIPOEMBALAGEM");
        String unidade = tipoEmb == null ? null : tipoEmb.startsWith("C") ? "UNID. DE COMPRA"
                : tipoEmb.startsWith("V") ? "UNID. DE VENDA" : null;
        String bonific = c.s("TIPOBONIFIC", "BONIFIC");
        String tipoPedido = bonific != null && !bonific.startsWith("N") ? "BONIFICACAO" : "COMPRA MERC.";

        return Optional.of(new PedidoWinthorImpressao(numPed, c.d("DTEMISSAO", "DATA"), unidade, tipoPedido,
                empresa, fornecedor, transportador, String.join(", ", prazos), frete(c.s("TIPOFRETE", "FRETE", "CIFFOB")),
                c.d("DTFATUR", "DTFATURAMENTO", "DTPREVFATUR"), c.d("DTEMBARQUE", "DTPREVEMBARQUE", "DTPREVEMB"),
                c.d("DTPREVENT", "DTPREVENTREGA", "DTENTREGA"),
                itens, totalBruto, desconto, totalLiquido,
                c.n("VLBASEICMS", "BASEICMS", "VLBCICMS"), c.n("VLICMS", "VLTOTALICMS"),
                c.n("VOLUME", "QTVOLUME", "VOLUMES", "NUMVOLUME"), c.n("PESO", "PESOBRUTO", "TOTPESO"),
                funcionario(c.l("CODFUNCEMISSAO", "CODEMITENTE", "CODFUNC", "CODFUNCINC", "CODCOMPRADOR")),
                funcionario(c.l("CODFUNCALT", "CODFUNCULTALTER", "CODFUNCALTER")),
                obs.isEmpty() ? null : String.join("\n", obs), origem, rodape));
    }

    /** Colunas e valores brutos (cabecalho, 1o item, filial, fornecedor) para ajustar o mapeamento. */
    public Map<String, Object> campos(Long numPed) {
        Map<String, Object> r = new LinkedHashMap<>();
        List<Map<String, Object>> cabs = jdbc.queryForList("SELECT * FROM PCPEDIDO WHERE NUMPED = ?", numPed);
        r.put("PCPEDIDO", cabs.isEmpty() ? null : cabs.get(0));
        List<Map<String, Object>> itens = jdbc.queryForList("SELECT * FROM PCITEM WHERE NUMPED = ?", numPed);
        r.put("PCITEM", itens.isEmpty() ? null : itens.get(0));
        if (!cabs.isEmpty()) {
            Linha c = new Linha(cabs.get(0));
            r.put("PCFILIAL", umaLinha("SELECT * FROM PCFILIAL WHERE CODIGO = ?", c.s("CODFILIAL")).m());
            r.put("PCFORNEC", umaLinha("SELECT * FROM PCFORNEC WHERE CODFORNEC = ?", c.l("CODFORNEC")).m());
        }
        return r;
    }

    private Linha umaLinha(String sql, Object chave) {
        if (chave == null) return new Linha(Map.of());
        try {
            List<Map<String, Object>> l = jdbc.queryForList(sql, chave);
            return new Linha(l.isEmpty() ? Map.of() : l.get(0));
        } catch (DataAccessException e) {
            log.warn("Impressao do pedido: falha em '{}': {}", sql, e.getMostSpecificCause().getMessage());
            return new Linha(Map.of());
        }
    }

    private String funcionario(Long matricula) {
        if (matricula == null || matricula == 0) return null;
        String nome = umaLinha("SELECT * FROM PCEMPR WHERE MATRICULA = ?", matricula).s("NOME", "NOMEGUERRA");
        return nome == null ? String.valueOf(matricula) : nome.split("\\s+")[0];
    }

    private static String frete(String v) {
        if (v == null) return null;
        return switch (v.trim().toUpperCase()) {
            case "C", "CIF" -> "CIF";
            case "F", "FOB" -> "FOB";
            default -> v.trim();
        };
    }

    /** Linha de SELECT * com acesso tolerante a nomes de coluna (o mapa do Spring ignora maiusculas). */
    record Linha(Map<String, Object> m) {
        List<String> colunas() {
            return m.keySet().stream().map(String::toUpperCase).sorted().toList();
        }

        Object v(String... nomes) {
            for (String n : nomes) {
                Object o = m.get(n);
                if (o == null) o = m.get(n.toLowerCase());
                if (o != null && !(o instanceof String s && s.isBlank())) return o;
            }
            return null;
        }

        String s(String... nomes) {
            Object o = v(nomes);
            return o == null ? null : o.toString().trim();
        }

        BigDecimal n(String... nomes) {
            Object o = v(nomes);
            if (o == null) return null;
            if (o instanceof BigDecimal b) return b;
            if (o instanceof Number x) return new BigDecimal(x.toString());
            try {
                return new BigDecimal(o.toString().trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        Long l(String... nomes) {
            BigDecimal b = n(nomes);
            return b == null ? null : b.longValue();
        }

        LocalDate d(String... nomes) {
            Object o = v(nomes);
            if (o instanceof Timestamp t) return t.toLocalDateTime().toLocalDate();
            if (o instanceof java.sql.Date d) return d.toLocalDate();
            if (o instanceof LocalDateTime t) return t.toLocalDate();
            if (o instanceof LocalDate d) return d;
            return null;
        }
    }
}
