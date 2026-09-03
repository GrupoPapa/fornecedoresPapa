package br.com.grupopapa.cfp.repository;

import br.com.grupopapa.cfp.domain.Fornecedor;
import br.com.grupopapa.cfp.domain.ProdutoErp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Grava o produto no WinThor reproduzindo a sequencia da rotina 203:
 * PCCONSUM (numero) -> PCPRODUT -> PCPRODFILIAL -> PCEST -> PCTABPR
 * -> PCEMBALAGEM -> PCBARRA -> PCCODFABRICA -> PKG_ESTOQUE.
 *
 * Tudo dentro de uma transacao propria da conexao do ERP.
 */
@Repository
public class ErpProdutoWriter {

    private static final Logger log = LoggerFactory.getLogger(ErpProdutoWriter.class);

    /** PCPRODUT.CODPROD e NUMBER(6): o WinThor nao aceita codigo acima disso. */
    private static final long CODPROD_MAXIMO = 999_999L;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final int rotina;
    private final String codFilial;

    public ErpProdutoWriter(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc,
                            @Qualifier("erpTransactionTemplate") TransactionTemplate transacao,
                            @Value("${app.erp.integracao.rotina:203}") int rotina,
                            @Value("${app.erp.integracao.codfilial:1}") String codFilial) {
        this.jdbc = jdbc;
        this.transacao = transacao;
        this.rotina = rotina;
        this.codFilial = codFilial;
    }

    /** Grava e devolve o CODPROD gerado. */
    public Long gravar(ProdutoErp e, Fornecedor fornecedor, Long matricula) {
        return transacao.execute(status -> {
            Long codProd = proximoCodProd();
            inserirProduto(codProd, e, fornecedor, matricula);
            inserirProdFilial(codProd);
            inserirEstoque(codProd);
            inserirTabelaPreco(codProd, matricula);
            inserirEmbalagem(codProd, e);
            inserirBarra(codProd, e, matricula);
            inserirCodFabrica(codProd);
            popularDeposito(codProd, matricula);
            return codProd;
        });
    }

    // ------------------------------------------------------------------ etapas

    /**
     * Reserva o proximo CODPROD do jeito que a rotina 203 faz: le PCCONSUM.PROXNUMPRODUT
     * com a linha travada (FOR UPDATE) e devolve o numero seguinte.
     *
     * Duas protecoes a mais, porque PCCONSUM pode estar zerado ou atrasado:
     *   - nunca devolve um numero menor que MAX(CODPROD) + 1 do PCPRODUT;
     *   - se mesmo assim o codigo ja existir, anda para o proximo livre.
     * Sem isso o cadastro sairia com um numero "aleatorio" (ou estouraria a PK).
     */
    private Long proximoCodProd() {
        // FOR UPDATE serializa duas gravacoes simultaneas na mesma linha de parametros
        List<Long> parametros = jdbc.queryForList(
                "SELECT NVL(PROXNUMPRODUT, 0) FROM PCCONSUM FOR UPDATE", Long.class);
        long proximoConfigurado = parametros.stream()
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .max().orElse(0L);

        Long maiorCadastrado = jdbc.queryForObject(
                "SELECT NVL(MAX(CODPROD), 0) FROM PCPRODUT", Long.class);
        long candidato = Math.max(proximoConfigurado,
                (maiorCadastrado == null ? 0L : maiorCadastrado) + 1);
        if (candidato < 1) {
            candidato = 1;
        }
        while (existeProduto(candidato)) {
            candidato++;
        }
        if (candidato > CODPROD_MAXIMO) {
            throw new IllegalStateException(
                    "CODPROD chegou ao limite do WinThor (" + CODPROD_MAXIMO + ").");
        }

        jdbc.update("UPDATE PCCONSUM SET PROXNUMPRODUT = ?", candidato + 1);
        log.info("CODPROD reservado: {} (PCCONSUM.PROXNUMPRODUT estava em {}, maior CODPROD {})",
                candidato, proximoConfigurado, maiorCadastrado);
        return candidato;
    }

    private boolean existeProduto(long codProd) {
        Integer achou = jdbc.queryForObject(
                "SELECT COUNT(1) FROM PCPRODUT WHERE CODPROD = ?", Integer.class, codProd);
        return achou != null && achou > 0;
    }

    /**
     * PCPRODUT.CODAUXILIAR e PCEMBALAGEM.CODAUXILIAR sao NUMBER - o codigo de barras
     * precisa ir como numero, nunca como texto.
     */
    private Long codigoBarras(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(valor.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Codigo de barras invalido para o WinThor (so digitos): " + valor);
        }
    }

    private void inserirProduto(Long codProd, ProdutoErp e, Fornecedor fornecedor, Long matricula) {
        String sql = """
            INSERT INTO PCPRODUT (
                 CODPROD, CODFILIAL, DESCRICAO, CODMARCA, CODFORNEC, CODEPTO, CODSEC
               , EMBALAGEM, UNIDADE, QTUNIT, UNIDADEMASTER, QTUNITCX, MULTIPLO
               , PESOLIQ, PESOBRUTO
               , CLASSIFICFISCAL, CODNCMEX, CODAUXILIAR, GTINCODAUXILIAR, CODFAB
               , SUGVENDA, PCOMEXT1, PCOMINT1, PCOMREP1
               , REVENDA, TEMREPOS, CESTABASICALEGIS, ACEITAVENDAFRACAO, ENVIAINFTECNICANFE
               , ENVIAECOMMERCE, USAWMS, TIPOCARGAPRODUTO, LOCALSEPARACAO, NATUREZAPRODUTO
               , DTCADASTRO, DTULTALTER, DTULTALTCAD
               , CODFUNCCADASTRO, CODFUNCULTALTER, CODFUNCULTALTCAD)
            VALUES (
                 ?, ?, ?, ?, ?, ?, ?
               , ?, ?, ?, ?, ?, ?
               , ?, ?
               , ?, ?, ?, ?, ?
               , ?, ?, ?, ?
               , ?, ?, ?, ?, ?
               , ?, ?, ?, ?, ?
               , TRUNC(SYSDATE), TRUNC(SYSDATE), TRUNC(SYSDATE)
               , ?, ?, ?)
            """;
        jdbc.update(sql,
                codProd, codFilial, e.getDescricao(), e.getCodMarca(),
                fornecedor.getCodFornecedorErp(), e.getCodEpto(), e.getCodSec(),
                e.getEmbalagem(), e.getUnidade(), e.getQtUnit(), e.getUnidadeMaster(),
                e.getQtUnitCx(), e.getMultiplo(),
                e.getPesoLiq(), e.getPesoBruto(),
                e.getClassificacaoFiscal(), e.getCodNcmEx(), codigoBarras(e.getCodAuxiliar()),
                e.getGtinCodAuxiliar(), e.getCodFab(),
                e.getSugVenda(), e.getPComExt1(), e.getPComInt1(), e.getPComRep1(),
                e.getRevenda(), e.getTemRepos(), e.getCestaBasicaLegis(),
                e.getAceitaVendaFracao(), e.getEnviaInfTecnicaNfe(),
                e.getEnviaEcommerce(), e.getUsaWms(), e.getTipoCargaProduto(),
                e.getLocalSeparacao(), e.getNaturezaProduto(),
                matricula, matricula, matricula);
    }

    private void inserirProdFilial(Long codProd) {
        jdbc.update("""
            INSERT INTO PCPRODFILIAL
                  (CODPROD, CODFILIAL, CODCOMPRADOR, PROIBIDAVENDA, FORALINHA, REVENDA,
                   ESTOQUEIDEAL, MULTIPLO, CHECARMULTIPLOVENDABNF, ACEITAVENDAFRACAO,
                   QTMINAUTOSERV, QTMINIMAATACADO, QTMINIMAATACADOF, ATIVO,
                   PISCOFINSRETIDO, PERPIS, PERCOFINS, CLASSE, CLASSEVENDA, CLASSEESTOQUE,
                   CODDISPESTRUTURA, PCOMREP1, PCOMINT1, PCOMEXT1, ESTOQUEPORSERIE)
            SELECT PCPRODUT.CODPROD, PCFILIAL.CODIGO, NULL,
                   DECODE(PCPRODUT.OBS, 'PV', 'S', 'N'),
                   DECODE(PCPRODUT.OBS2, 'FL', 'S', 'N'),
                   PCPRODUT.REVENDA, 0,
                   NVL(PCPRODUT.MULTIPLO, 1),
                   NVL(PCPRODUT.CHECARMULTIPLOVENDABNF, 'N'),
                   NVL(PCPRODUT.ACEITAVENDAFRACAO, 'N'),
                   0, 0, 0, 'S',
                   NVL(PCPRODUT.PISCOFINSRETIDO, 'N'), NULL, NULL,
                   PCPRODUT.CLASSE, PCPRODUT.CLASSEVENDA, PCPRODUT.CLASSEESTOQUE, 0,
                   PCPRODUT.PCOMREP1, PCPRODUT.PCOMINT1, PCPRODUT.PCOMEXT1, 'N'
              FROM PCPRODUT, PCFILIAL
             WHERE PCFILIAL.CODIGO <> '99'
               AND PCFILIAL.CODIGO IS NOT NULL
               AND PCPRODUT.CODPROD = ?
               AND NOT EXISTS (SELECT 1 FROM PCPRODFILIAL
                                WHERE PCPRODFILIAL.CODPROD = PCPRODUT.CODPROD
                                  AND PCPRODFILIAL.CODFILIAL = PCFILIAL.CODIGO)
            """, codProd);
    }

    private void inserirEstoque(Long codProd) {
        jdbc.update("""
            INSERT INTO PCEST (CODFILIAL, CODPROD)
            SELECT PCFILIAL.CODIGO, ?
              FROM PCFILIAL
             WHERE PCFILIAL.CODIGO <> '99'
               AND NOT EXISTS (SELECT 1 FROM PCEST
                                WHERE PCEST.CODPROD = ?
                                  AND PCEST.CODFILIAL = PCFILIAL.CODIGO)
            """, codProd, codProd);
    }

    private void inserirTabelaPreco(Long codProd, Long matricula) {
        jdbc.update("""
            INSERT INTO PCTABPR
                  (CODPROD, NUMREGIAO, VLACRESFRETEKG, MARGEM, ROTINA, MATRICULA,
                   PERDESCMAXIDEALTAB, PERDESCMAXPOSSIVELTAB, PERDESCMAXAVISTATAB,
                   PERCCOMGARANTIDATAB)
            SELECT PCPRODUT.CODPROD, PCREGIAO.NUMREGIAO,
                   NVL(PCREGIAO.VLFRETEKGPADRAO, 0), PCCONSUM.MARGEMPREV, ?, ?,
                   NVL(PCDEPTO.PERDESCMAXIDEAL, 0), NVL(PCDEPTO.PERDESCMAXPOSSIVEL, 0),
                   NVL(PCDEPTO.PERDESCMAXAVISTA, 0), NVL(PCDEPTO.PERCCOMGARANTIDA, 0)
              FROM PCCONSUM, PCREGIAO, PCDEPTO, PCPRODUT
             WHERE PCDEPTO.CODEPTO = PCPRODUT.CODEPTO
               AND PCPRODUT.CODPROD = ?
               AND NOT EXISTS (SELECT 1 FROM PCTABPR
                                WHERE PCTABPR.NUMREGIAO = PCREGIAO.NUMREGIAO
                                  AND PCTABPR.CODPROD = PCPRODUT.CODPROD)
            """, rotina, matricula, codProd);
    }

    private void inserirEmbalagem(Long codProd, ProdutoErp e) {
        jdbc.update("""
            INSERT INTO PCEMBALAGEM
                  (USABALANCATOLEDO, PERMITEMULTIPLICACAO, TIPOEMBALAGEM, CODPROD,
                   CODAUXILIAR, CODFILIAL, EMBALAGEM, UNIDADE, QTUNIT,
                   MARGEM_ESP, PVENDA, PTABELA, MARGEM, ENVIABALANCA, ENVIAFV)
            SELECT 'N', 'N', 'V', ?, ?, PCFILIAL.CODIGO, ?, ?, ?,
                   0, 0, 0, 0, 'N', 'S'
              FROM PCFILIAL
             WHERE PCFILIAL.CODIGO <> '99'
               AND PCFILIAL.INCLUIRITEMPCEMBALAGEM = 'S'
               AND PCFILIAL.DTEXCLUSAO IS NULL
               AND NOT EXISTS (SELECT 1 FROM PCEMBALAGEM
                                WHERE PCEMBALAGEM.CODAUXILIAR = ?
                                  AND PCEMBALAGEM.CODFILIAL = PCFILIAL.CODIGO)
            """, codProd, codigoBarras(e.getCodAuxiliar()), e.getEmbalagem(), e.getUnidade(),
                 e.getQtUnit() == null ? BigDecimal.ONE : e.getQtUnit(),
                 codigoBarras(e.getCodAuxiliar()));
    }

    private void inserirBarra(Long codProd, ProdutoErp e, Long matricula) {
        if (e.getCodAuxiliar() == null || e.getCodAuxiliar().isBlank()) {
            return;
        }
        jdbc.update("""
            INSERT INTO PCBARRA (CODPROD, CODBARRA, DTCADASTRO, CODFUNCCAD, CODFUNCALT, DTULTALT)
            SELECT ?, ?, TRUNC(SYSDATE), ?, ?, TRUNC(SYSDATE) FROM DUAL
             WHERE NOT EXISTS (SELECT 1 FROM PCBARRA WHERE CODBARRA = ?)
            """, codProd, e.getCodAuxiliar(), matricula, matricula, e.getCodAuxiliar());
    }

    private void inserirCodFabrica(Long codProd) {
        jdbc.update("""
            INSERT INTO PCCODFABRICA (CODPROD, CODFORNEC, CODFAB, FATOR)
            SELECT CODPROD, CODFORNEC, CODFAB, QTUNITCX
              FROM PCPRODUT
             WHERE CODPROD = ?
               AND CODFORNEC IS NOT NULL
               AND CODFAB IS NOT NULL
               AND NOT EXISTS (SELECT 1 FROM PCCODFABRICA
                                WHERE PCCODFABRICA.CODPROD = PCPRODUT.CODPROD
                                  AND PCCODFABRICA.CODFORNEC = PCPRODUT.CODFORNEC
                                  AND PCCODFABRICA.CODFAB = PCPRODUT.CODFAB)
            """, codProd);
    }

    /** Popula o deposito padrao. Falha aqui nao invalida o cadastro. */
    private void popularDeposito(Long codProd, Long matricula) {
        try {
            jdbc.update("""
                DECLARE vMsg VARCHAR2(4000);
                BEGIN
                  PKG_ESTOQUE.DEPOSITO_POPULAR_ESTOQUE('99', ?, TRUE, vMsg, ?);
                END;
                """, matricula, codProd);
        } catch (Exception ex) {
            log.warn("PKG_ESTOQUE.DEPOSITO_POPULAR_ESTOQUE falhou para o produto {}: {}",
                    codProd, ex.getMessage());
        }
    }
}
