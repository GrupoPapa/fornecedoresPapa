package br.com.grupopapa.cfp.repository;

import br.com.grupopapa.cfp.domain.PedidoCompra;
import br.com.grupopapa.cfp.domain.PedidoCompraItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;

/**
 * Grava a sugestao de compra que a rotina 220 ("Digitar pedido de compra") importa:
 * PCSUGESTAOCOMPRAC (cabecalho) + PCSUGESTAOCOMPRAI (itens), numa transacao so.
 *
 * Campos preenchidos conforme o que a 220 le (ver log220):
 *   - IMPORTACAO = 'N'  -> a 220 so lista sugestoes com IMPORTACAO <> 'S';
 *   - STATUS do item = 'P' (pendente), QTPEDIDO nulo -> igual as sugestoes pendentes da base;
 *     a 220 so lista se houver item com STATUS <> 'F' e troca para 'F' ao gerar o pedido;
 *   - TIPOEMBALAGEMPEDIDO = 'V' (padrao de todas as sugestoes da base);
 *   - DATASUGESTAO = TRUNC(SYSDATE): a 220 filtra "BETWEEN :DTINICIO AND :DTFIM" com o
 *     fim a meia-noite; com hora, a sugestao de hoje so aparece filtrando ate amanha.
 */
@Repository
public class ErpSugestaoWriter {

    private static final Logger log = LoggerFactory.getLogger(ErpSugestaoWriter.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final String tabelaCab;
    private final String tabelaItem;
    private final String sequence;
    private final String statusItem;
    private final boolean preencherQtPedido;
    private final String tipoEmbalagem;

    public ErpSugestaoWriter(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc,
                             @Qualifier("erpTransactionTemplate") TransactionTemplate transacao,
                             @Value("${app.erp.compra.tabela-sugestao:PCSUGESTAOCOMPRAC}") String tabelaCab,
                             @Value("${app.erp.compra.tabela-sugestao-item:PCSUGESTAOCOMPRAI}") String tabelaItem,
                             @Value("${app.erp.compra.sequence-sugestao:}") String sequence,
                             @Value("${app.erp.compra.status-item:P}") String statusItem,
                             @Value("${app.erp.compra.preencher-qtpedido:false}") boolean preencherQtPedido,
                             @Value("${app.erp.compra.tipo-embalagem:V}") String tipoEmbalagem) {
        this.jdbc = jdbc;
        this.transacao = transacao;
        this.tabelaCab = ErpCompraRepository.validar(tabelaCab);
        this.tabelaItem = ErpCompraRepository.validar(tabelaItem);
        this.sequence = (sequence == null || sequence.isBlank()) ? null : ErpCompraRepository.validar(sequence.trim());
        this.statusItem = (statusItem == null || statusItem.isBlank()) ? null : statusItem.trim().substring(0, 1);
        this.preencherQtPedido = preencherQtPedido;
        this.tipoEmbalagem = (tipoEmbalagem == null || tipoEmbalagem.isBlank()) ? null : tipoEmbalagem.trim().substring(0, 1);
    }

    /** Grava a sugestao e devolve o NUMSUGESTAO gerado. */
    public Long gravar(PedidoCompra pedido, Long codFornec, Long matricula) {
        return transacao.execute(status -> {
            Long numero = proximoNumero();

            jdbc.update("INSERT INTO " + tabelaCab + " ("
                            + " NUMSUGESTAO, CODFORNEC, CODFILIAL, CODUSUARIOSUGESTAO, DATASUGESTAO,"
                            + " CODEDITAL, TIPODESCARGA, IMPORTACAO, TIPOEMBALAGEMPEDIDO, DTPREVENT)"
                            + " VALUES (?, ?, ?, ?, TRUNC(SYSDATE), NULL, ?, 'N', ?, ?)",
                    numero, codFornec, pedido.getCodFilial(), matricula,
                    pedido.getTipo().getTipoDescarga(), tipoEmbalagem,
                    pedido.getDataPrevisaoEntrega() == null ? null : Date.valueOf(pedido.getDataPrevisaoEntrega()));

            for (PedidoCompraItem item : pedido.getItens()) {
                jdbc.update("INSERT INTO " + tabelaItem + " ("
                                + " NUMSUGESTAO, CODPROD, QTSUGERIDA, QTPEDIDO, STATUS, PCOMPRALIQSUGERIDO, OBS)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                        numero, item.getCodProdErp(), item.getQuantidade(),
                        preencherQtPedido ? item.getQuantidade() : null,
                        statusItem, item.getPrecoUnitario(),
                        obs(pedido, item));
            }
            log.info("Sugestao {} gravada no WinThor para o pedido {} ({} itens)",
                    numero, pedido.getNumero(), pedido.getItens().size());
            return numero;
        });
    }

    /** OBS do item (200): leva o numero do pedido do portal para rastrear na 220. */
    private String obs(PedidoCompra pedido, PedidoCompraItem item) {
        String texto = "Portal " + pedido.getNumero()
                + (item.getObservacao() == null || item.getObservacao().isBlank() ? "" : " - " + item.getObservacao().trim());
        return texto.length() <= 200 ? texto : texto.substring(0, 200);
    }

    /**
     * Com sequence configurada, usa NEXTVAL. Sem ela, trava a tabela de sugestoes
     * (outras integracoes, como o Systock, tambem gravam ali) e usa MAX + 1.
     */
    private Long proximoNumero() {
        if (sequence != null) {
            return jdbc.queryForObject("SELECT " + sequence + ".NEXTVAL FROM DUAL", Long.class);
        }
        jdbc.execute("LOCK TABLE " + tabelaCab + " IN EXCLUSIVE MODE");
        Long maior = jdbc.queryForObject("SELECT NVL(MAX(NUMSUGESTAO), 0) FROM " + tabelaCab, Long.class);
        return (maior == null ? 0L : maior) + 1;
    }
}
