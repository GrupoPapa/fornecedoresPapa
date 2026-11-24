package br.com.grupopapa.cfp.systock;

import br.com.grupopapa.cfp.domain.PedidoCompra;
import br.com.grupopapa.cfp.security.UsuarioLogado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Sugestoes de compra do Systock. O fornecedor ve so as dele; a equipe interna ve todas
 * (ou as de um fornecedor, com fornecedorId).
 */
@RestController
@RequestMapping("/api/systock/pedidos-compra")
@Tag(name = "Systock - sugestoes de compra")
public class SystockController {

    private final SystockService service;

    public SystockController(SystockService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Sugestoes de compra do Systock (em aberto, em cotacao, para exportacao e finalizadas recentes)")
    public SugestoesSystockResponse listar(@RequestParam(required = false) Long fornecedorId,
                                           @RequestParam(defaultValue = "false") boolean atualizar) {
        return service.listar(UsuarioLogado.obter(), fornecedorId, atualizar);
    }

    @PostMapping("/{idSystock}/importar")
    @Operation(summary = "Traz a sugestao para o portal como pedido de compra em digitacao")
    public Map<String, Object> importar(@PathVariable long idSystock) {
        PedidoCompra p = service.importar(idSystock, UsuarioLogado.obter());
        return Map.of("pedidoId", p.getId(), "pedidoNumero", p.getNumero(), "systockId", idSystock);
    }
}
