package br.com.grupopapa.cfp.controller;

import br.com.grupopapa.cfp.domain.PedidoCompra;
import br.com.grupopapa.cfp.dto.AnaliseCompraRequest;
import br.com.grupopapa.cfp.dto.ComparativoPrecoResponse;
import br.com.grupopapa.cfp.dto.PedidoCompraResponse;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import br.com.grupopapa.cfp.security.UsuarioLogado;
import br.com.grupopapa.cfp.service.PedidoCompraService;
import br.com.grupopapa.cfp.service.SugestaoCompraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Analise do setor fiscal. Todas as rotas exigem perfil ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/pedidos-compra")
@Tag(name = "Admin - Pedidos de compra")
public class AdminPedidoCompraController {

    private final PedidoCompraService service;
    private final SugestaoCompraService sugestaoService;

    public AdminPedidoCompraController(PedidoCompraService service, SugestaoCompraService sugestaoService) {
        this.service = service;
        this.sugestaoService = sugestaoService;
    }

    @GetMapping("/{id}/comparativo")
    @Operation(summary = "Comparativo de cada item com as ultimas entradas no WinThor")
    public ComparativoPrecoResponse comparativo(@PathVariable Long id) {
        return service.comparativo(id, UsuarioLogado.obter());
    }

    @PostMapping("/{id}/resolver-divergencia")
    @Operation(summary = "Fiscal reanalisa o cadastro e libera o pedido para a analise de precos")
    public PedidoCompraResponse resolverDivergencia(@PathVariable Long id,
                                                    @RequestBody(required = false) @Valid AnaliseCompraRequest request) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        service.resolverDivergencia(id, request, logado);
        return PedidoCompraResponse.completo(service.buscar(id, logado));
    }

    @PostMapping("/{id}/aprovar")
    @Operation(summary = "Aprova e, se habilitado, gera a sugestao na PCSUGESTAOCOMPRAC/I")
    public PedidoCompraResponse aprovar(@PathVariable Long id,
                                        @RequestBody(required = false) @Valid AnaliseCompraRequest request) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        service.aprovar(id, request, logado);
        if (sugestaoService.isHabilitada()) {
            sugestaoService.gerar(id, logado);
        }
        return PedidoCompraResponse.completo(service.buscar(id, logado));
    }

    @PostMapping("/{id}/gerar-sugestao")
    @Operation(summary = "Gera (ou tenta de novo) a sugestao no WinThor de um pedido aprovado")
    public PedidoCompraResponse gerarSugestao(@PathVariable Long id) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        PedidoCompra p = sugestaoService.gerar(id, logado);
        return PedidoCompraResponse.completo(service.buscar(p.getId(), logado));
    }

    @PostMapping("/{id}/revisar")
    @Operation(summary = "Devolve ao fornecedor com o motivo, para ele corrigir e reenviar")
    public PedidoCompraResponse revisar(@PathVariable Long id,
                                        @RequestBody(required = false) @Valid AnaliseCompraRequest request) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        service.revisar(id, request == null ? null : request.parecer(), logado);
        return PedidoCompraResponse.completo(service.buscar(id, logado));
    }

    @PostMapping("/{id}/reprovar")
    @Operation(summary = "Reprova e encerra o pedido")
    public PedidoCompraResponse reprovar(@PathVariable Long id,
                                         @RequestBody(required = false) @Valid AnaliseCompraRequest request) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        service.reprovar(id, request == null ? null : request.parecer(), logado);
        return PedidoCompraResponse.completo(service.buscar(id, logado));
    }

    @PostMapping("/{id}/voltar-analise")
    @Operation(summary = "Volta um pedido aprovado (sem sugestao gerada) para analise")
    public PedidoCompraResponse voltarAnalise(@PathVariable Long id) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        service.voltarParaAnalise(id, logado);
        return PedidoCompraResponse.completo(service.buscar(id, logado));
    }
}
