package br.com.grupopapa.cfp.controller;

import br.com.grupopapa.cfp.dto.AnaliseRequest;
import br.com.grupopapa.cfp.dto.PedidoResponse;
import br.com.grupopapa.cfp.security.UsuarioLogado;
import br.com.grupopapa.cfp.service.IntegracaoErpService;
import br.com.grupopapa.cfp.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/pedidos")
@Tag(name = "Admin - Pedidos")
public class AdminPedidoController {

    private final PedidoService pedidoService;
    private final IntegracaoErpService integracaoService;

    public AdminPedidoController(PedidoService pedidoService,
                                 IntegracaoErpService integracaoService) {
        this.pedidoService = pedidoService;
        this.integracaoService = integracaoService;
    }

    @PostMapping("/{id}/integrar")
    @Operation(summary = "Grava no WinThor todos os itens do pedido aprovado")
    public IntegracaoErpService.ResultadoIntegracao integrar(@PathVariable Long id) {
        return integracaoService.integrarPedido(id, UsuarioLogado.obter());
    }

    @PostMapping("/{id}/aprovar")
    @Operation(summary = "Aprova o pedido e todos os itens dele")
    public PedidoResponse aprovar(@PathVariable Long id,
                                  @RequestBody(required = false) @Valid AnaliseRequest request) {
        String parecer = request != null ? request.parecer() : null;
        return PedidoResponse.de(pedidoService.analisar(id, true, parecer, UsuarioLogado.obter()));
    }

    @PostMapping("/{id}/reprovar")
    @Operation(summary = "Devolve o pedido ao fornecedor; itens sem ressalva ficam aprovados")
    public PedidoResponse reprovar(@PathVariable Long id,
                                   @RequestBody(required = false) @Valid AnaliseRequest request) {
        String parecer = request != null ? request.parecer() : null;
        return PedidoResponse.de(pedidoService.analisar(id, false, parecer, UsuarioLogado.obter()));
    }

    @PostMapping("/{id}/reabrir")
    @Operation(summary = "Volta um pedido aprovado para analise")
    public PedidoResponse reabrir(@PathVariable Long id) {
        return PedidoResponse.de(pedidoService.reabrir(id, UsuarioLogado.obter()));
    }
}
