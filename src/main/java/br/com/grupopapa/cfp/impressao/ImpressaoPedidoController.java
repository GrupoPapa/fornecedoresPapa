package br.com.grupopapa.cfp.impressao;

import br.com.grupopapa.cfp.exception.RecursoNaoEncontradoException;
import br.com.grupopapa.cfp.security.UsuarioLogado;
import br.com.grupopapa.cfp.service.SugestaoCompraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/** Dados para imprimir o pedido de compra do WinThor no layout da rotina 210. */
@RestController
@Tag(name = "Impressao do pedido de compra (WinThor)")
public class ImpressaoPedidoController {

    private final ErpPedidoImpressaoRepository erp;
    private final SugestaoCompraService sugestaoService;

    public ImpressaoPedidoController(ErpPedidoImpressaoRepository erp, SugestaoCompraService sugestaoService) {
        this.erp = erp;
        this.sugestaoService = sugestaoService;
    }

    /** Pedido do WinThor gerado a partir de um pedido do portal (fornecedor dono ou admin). */
    @GetMapping("/api/pedidos-compra/{id}/winthor/pedidos/{numPed}/impressao")
    @Operation(summary = "Pedido do WinThor gerado pela sugestao deste pedido do portal, para imprimir")
    public PedidoWinthorImpressao doPedidoDoPortal(@PathVariable Long id, @PathVariable Long numPed) {
        var situacao = sugestaoService.situacao(id, UsuarioLogado.obter()); // valida o acesso ao pedido
        boolean doPedido = situacao.itens().stream().anyMatch(i -> Objects.equals(i.numPed(), numPed));
        if (!doPedido) {
            throw new RecursoNaoEncontradoException("O pedido " + numPed + " do WinThor nao foi gerado a partir deste pedido.");
        }
        return buscar(numPed);
    }

    /** Qualquer pedido de compra do WinThor (so admin: rota /api/admin). */
    @GetMapping("/api/admin/pedidos-compra/winthor/{numPed}/impressao")
    @Operation(summary = "Qualquer pedido de compra do WinThor, para imprimir (admin)")
    public PedidoWinthorImpressao qualquer(@PathVariable Long numPed) {
        return buscar(numPed);
    }

    /** Colunas reais de PCPEDIDO/PCITEM/PCFILIAL/PCFORNEC, para ajustar a impressao (admin). */
    @GetMapping("/api/admin/pedidos-compra/winthor/{numPed}/campos")
    @Operation(summary = "Diagnostico: colunas e valores do pedido no WinThor (admin)")
    public Map<String, Object> campos(@PathVariable Long numPed) {
        return erp.campos(numPed);
    }

    private PedidoWinthorImpressao buscar(Long numPed) {
        return erp.buscar(numPed).orElseThrow(() ->
                new RecursoNaoEncontradoException("Pedido de compra " + numPed + " nao encontrado no WinThor."));
    }
}
