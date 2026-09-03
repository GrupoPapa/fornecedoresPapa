package br.com.grupopapa.cfp.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * Duas conexoes:
 *   - a principal (@Primary), onde ficam as tabelas CFP_ do portal;
 *   - a do WinThor, usada para ler PCFORNEC/PCPRODUT/PCMARCA e gravar o produto.
 *
 * Se as variaveis do ERP nao forem informadas, a segunda aponta para a mesma base
 * da primeira. Assim da para testar a gravacao em homologacao sem mover o portal.
 *
 * Importante: o transaction manager do ERP NAO e um bean, para nao competir com o
 * do JPA - o portal continua transacionando normalmente na base dele.
 */
@Configuration
public class ErpDataSourceConfig {

    // ---------------------------------------------------------------- portal

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties dataSourceProperties) {
        return dataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();
    }

    // ------------------------------------------------------------------ ERP

    @Bean
    public DataSource erpDataSource(
            @Value("${app.erp.datasource.dsn:}") String dsn,
            @Value("${app.erp.datasource.usuario:}") String usuario,
            @Value("${app.erp.datasource.senha:}") String senha,
            @Value("${app.erp.datasource.pool:5}") int pool,
            DataSourceProperties portal) {

        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("erp-pool");
        ds.setDriverClassName("oracle.jdbc.OracleDriver");
        ds.setJdbcUrl(dsn == null || dsn.isBlank()
                ? portal.getUrl() : "jdbc:oracle:thin:@" + dsn.trim());
        ds.setUsername(usuario == null || usuario.isBlank() ? portal.getUsername() : usuario.trim());
        ds.setPassword(senha == null || senha.isBlank() ? portal.getPassword() : senha);
        ds.setMaximumPoolSize(pool);
        ds.setMinimumIdle(1);
        ds.setConnectionTimeout(30000);
        return ds;
    }

    @Bean
    public JdbcTemplate erpJdbcTemplate(@Qualifier("erpDataSource") DataSource erpDataSource) {
        return new JdbcTemplate(erpDataSource);
    }

    /** Transacao usada na gravacao do produto no WinThor (tudo ou nada). */
    @Bean
    public TransactionTemplate erpTransactionTemplate(@Qualifier("erpDataSource") DataSource erpDataSource) {
        return new TransactionTemplate(new DataSourceTransactionManager(erpDataSource));
    }
}
