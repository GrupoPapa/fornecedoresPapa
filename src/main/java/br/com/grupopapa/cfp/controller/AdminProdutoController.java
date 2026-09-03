package br.com.grupopapa.cfp.controller;

import br.com.grupopapa.cfp.dto.AnaliseRequest;
import br.com.grupopapa.cfp.dto.ProdutoResponse;
import br.com.grupopapa.cfp.security.UsuarioLogado;
import br.com.grupopapa.cfp.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/produtos")
@Tag(name = "Admin - Produtos")
public class AdminProdutoController {

    private final ProdutoService produtoService;

    public AdminProdutoController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @PostMapping("/{id}/aprovar")
    @Operation(summary = "Aprova um cadastro enviado pelo fornecedor")
    public ProdutoResponse aprovar(@PathVariable Long id, @RequestBody(required = false) @Valid AnaliseRequest request) {
        String parecer = request != null ? request.parecer() : null;
        return ProdutoResponse.de(produtoService.analisar(id, true, parecer, UsuarioLogado.obter()));
    }

    @PostMapping("/{id}/reprovar")
    @Operation(summary = "Marca ressalva no item (dentro de um pedido) ou reprova o cadastro avulso")
    public ProdutoResponse reprovar(@PathVariable Long id, @RequestBody @Valid AnaliseRequest request) {
        return ProdutoResponse.de(produtoService.analisar(id, false, request.parecer(), UsuarioLogado.obter()));
    }

    @PostMapping("/{id}/retirar-ressalva")
    @Operation(summary = "Desfaz a ressalva do item e devolve para a analise do pedido")
    public ProdutoResponse retirarRessalva(@PathVariable Long id) {
        return ProdutoResponse.de(produtoService.retirarRessalva(id, UsuarioLogado.obter()));
    }

    @PostMapping("/{id}/reabrir")
    @Operation(summary = "Volta o produto para rascunho (permite nova edicao do fornecedor)")
    public ProdutoResponse reabrir(@PathVariable Long id) {
        return ProdutoResponse.de(produtoService.reabrir(id, UsuarioLogado.obter()));
    }
}
