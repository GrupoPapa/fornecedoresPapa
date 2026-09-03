package br.com.grupopapa.cfp.controller;

import br.com.grupopapa.cfp.domain.Pedido;
import br.com.grupopapa.cfp.domain.StatusPedido;
import br.com.grupopapa.cfp.dto.*;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import br.com.grupopapa.cfp.security.UsuarioLogado;
import br.com.grupopapa.cfp.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
@Tag(name = "Pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping
    @Operation(summary = "Lista os pedidos do fornecedor logado (admin ve todos)")
    public PaginaResponse<PedidoResponse> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(required = false) Long fornecedorId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {

        UsuarioAutenticado logado = UsuarioLogado.obter();
        Long escopo = logado.isAdmin() ? fornecedorId : logado.getFornecedorId();
        Page<Pedido> page = pedidoService.listar(escopo, status, termo,
                PageRequest.of(pagina, Math.min(tamanho, 200), Sort.by(Sort.Direction.DESC, "id")));
        return PaginaResponse.de(page, PedidoResponse::de);
    }

    @GetMapping("/aberto")
    @Operation(summary = "Devolve o pedido em aberto do fornecedor logado, se houver")
    public PedidoResponse aberto() {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        if (logado.getFornecedorId() == null) {
            return null;
        }
        Pedido pedido = pedidoService.pedidoAberto(logado.getFornecedorId());
        return pedido == null ? null : PedidoResponse.de(pedido);
    }

    @PostMapping("/abrir")
    @Operation(summary = "Inicia um pedido (ou devolve o que ja estiver aberto)")
    public PedidoResponse abrir(@RequestParam(required = false) Long fornecedorId) {
        return PedidoResponse.de(pedidoService.abrirOuObter(UsuarioLogado.obter(), fornecedorId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Pedido com todos os itens")
    public PedidoResponse buscar(@PathVariable Long id) {
        Pedido pedido = pedidoService.buscar(id, UsuarioLogado.obter());
        List<ProdutoResponse> itens = pedidoService.itens(id).stream().map(ProdutoResponse::de).toList();
        return PedidoResponse.comItens(pedido, itens);
    }

    @PutMapping("/{id}")
    public PedidoResponse atualizar(@PathVariable Long id, @RequestBody @Valid PedidoRequest request) {
        return PedidoResponse.de(pedidoService.atualizar(id, request, UsuarioLogado.obter()));
    }

    @PostMapping("/{id}/enviar")
    @Operation(summary = "Envia o pedido inteiro para analise da empresa")
    public PedidoResponse enviar(@PathVariable Long id) {
        return PedidoResponse.de(pedidoService.enviar(id, UsuarioLogado.obter()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancela um pedido ainda em aberto, com os itens dele")
    public void excluir(@PathVariable Long id) {
        pedidoService.excluir(id, UsuarioLogado.obter());
    }
}
