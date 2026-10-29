package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.PedidoCompra;
import br.com.grupopapa.cfp.domain.StatusPedidoCompra;
import br.com.grupopapa.cfp.exception.NegocioException;
import br.com.grupopapa.cfp.repository.ErpCompraRepository;
import br.com.grupopapa.cfp.repository.ErpSugestaoWriter;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * "Gerar pedido para importar na 220": grava a sugestao no WinThor depois da aprovacao.
 *
 * Fica fora da transacao do portal de proposito: primeiro o ERP confirma a gravacao,
 * depois o portal registra o numero. Se o ERP falhar, o pedido continua aprovado com a
 * mensagem de erro e o fiscal pode tentar de novo.
 */
@Service
public class SugestaoCompraService {

    private static final Logger log = LoggerFactory.getLogger(SugestaoCompraService.class);

    private final PedidoCompraService pedidoService;
    private final ErpSugestaoWriter writer;
    private final ErpCompraRepository erp;
    private final boolean habilitada;
    private final Long matricula;

    public SugestaoCompraService(PedidoCompraService pedidoService,
                                 ErpSugestaoWriter writer,
                                 ErpCompraRepository erp,
                                 @Value("${app.erp.compra.gerar-sugestao:false}") boolean habilitada,
                                 @Value("${app.erp.matricula:}") String matricula) {
        this.pedidoService = pedidoService;
        this.writer = writer;
        this.erp = erp;
        this.habilitada = habilitada;
        this.matricula = converter(matricula);
    }

    private static Long converter(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return Long.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            log.warn("CFP_ERP_MATRICULA invalida ({}) - a geracao da sugestao ficara bloqueada.", valor);
            return null;
        }
    }

    public boolean isHabilitada() {
        return habilitada;
    }

    /** synchronized: evita gerar duas sugestoes para o mesmo pedido com cliques simultaneos. */
    public synchronized PedidoCompra gerar(Long pedidoId, UsuarioAutenticado logado) {
        PedidoCompra pedido = pedidoService.buscar(pedidoId, logado);
        if (pedido.getStatus() != StatusPedidoCompra.APROVADO) {
            throw new NegocioException("A sugestao so e gerada para pedidos aprovados.");
        }
        if (pedido.isSugestaoGerada()) {
            throw new NegocioException("A sugestao " + pedido.getNumSugestaoErp() + " ja foi gerada para este pedido.");
        }
        if (!habilitada) {
            throw new NegocioException("A geracao da sugestao no WinThor esta desligada. "
                    + "Ligue em CFP_ERP_SUGESTAO para habilitar.");
        }
        if (matricula == null) {
            throw new NegocioException("Informe a matricula do WinThor em CFP_ERP_MATRICULA "
                    + "(vai em PCSUGESTAOCOMPRAC.CODUSUARIOSUGESTAO).");
        }

        try {
            Long numero = writer.gravar(pedido, pedido.getFornecedor().getCodFornecedorErp(), matricula);
            return pedidoService.registrarSugestao(pedidoId, numero, null, logado.getId());
        } catch (Exception e) {
            String causa = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            log.error("Falha ao gerar a sugestao do pedido {}: {}", pedido.getNumero(), causa);
            return pedidoService.registrarSugestao(pedidoId, null, causa, logado.getId());
        }
    }

    /** Situacao da sugestao no WinThor: se ja foi importada na 220 e o pedido gerado. */
    public ErpCompraRepository.SituacaoSugestao situacao(Long pedidoId, UsuarioAutenticado logado) {
        PedidoCompra pedido = pedidoService.buscar(pedidoId, logado);
        if (pedido.getNumSugestaoErp() == null) {
            return new ErpCompraRepository.SituacaoSugestao(false, false, java.util.List.of());
        }
        return erp.situacaoSugestao(pedido.getNumSugestaoErp());
    }
}
