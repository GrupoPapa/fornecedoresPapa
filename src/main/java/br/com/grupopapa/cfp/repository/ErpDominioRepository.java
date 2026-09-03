package br.com.grupopapa.cfp.repository;

import br.com.grupopapa.cfp.dto.DominioResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Listas do WinThor usadas nos campos do complemento (somente leitura).
 * Se a tabela nao existir ou o usuario nao tiver permissao, devolve lista vazia
 * e a tela deixa o campo livre para digitacao.
 */
@Repository
public class ErpDominioRepository {

    private static final Logger log = LoggerFactory.getLogger(ErpDominioRepository.class);

    private static final RowMapper<DominioResponse> MAPPER = (rs, i) ->
            new DominioResponse(rs.getString(1), rs.getString(2));

    private final JdbcTemplate jdbcTemplate;
    private final String tabelaMarca;
    private final String tabelaDepto;
    private final String tabelaSecao;

    public ErpDominioRepository(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbcTemplate,
                                @Value("${app.erp.tabela-marca:PCMARCA}") String tabelaMarca,
                                @Value("${app.erp.tabela-departamento:PCDEPTO}") String tabelaDepto,
                                @Value("${app.erp.tabela-secao:PCSECAO}") String tabelaSecao) {
        this.jdbcTemplate = jdbcTemplate;
        this.tabelaMarca = validar(tabelaMarca);
        this.tabelaDepto = validar(tabelaDepto);
        this.tabelaSecao = validar(tabelaSecao);
    }

    private static String validar(String nome) {
        if (!nome.matches("[A-Za-z0-9_.$]+")) {
            throw new IllegalStateException("Nome de tabela invalido: " + nome);
        }
        return nome;
    }

    public List<DominioResponse> marcas() {
        return consultar("SELECT TO_CHAR(CODMARCA), MARCA FROM " + tabelaMarca
                + " ORDER BY MARCA", "marcas");
    }

    public List<DominioResponse> departamentos() {
        return consultar("SELECT TO_CHAR(CODEPTO), DESCRICAO FROM " + tabelaDepto
                + " ORDER BY DESCRICAO", "departamentos");
    }

    public List<DominioResponse> secoes(Integer codEpto) {
        if (codEpto == null) {
            return consultar("SELECT TO_CHAR(CODSEC), DESCRICAO FROM " + tabelaSecao
                    + " ORDER BY DESCRICAO", "secoes");
        }
        try {
            return jdbcTemplate.query("SELECT TO_CHAR(CODSEC), DESCRICAO FROM " + tabelaSecao
                    + " WHERE CODEPTO = ? ORDER BY DESCRICAO", MAPPER, codEpto);
        } catch (DataAccessException e) {
            log.warn("Nao foi possivel ler as secoes do WinThor: {}", e.getMostSpecificCause().getMessage());
            return List.of();
        }
    }

    private List<DominioResponse> consultar(String sql, String descricao) {
        try {
            return jdbcTemplate.query(sql, MAPPER);
        } catch (DataAccessException e) {
            log.warn("Nao foi possivel ler {} do WinThor: {}", descricao,
                    e.getMostSpecificCause().getMessage());
            return List.of();
        }
    }
}
