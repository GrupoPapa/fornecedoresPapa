package br.com.grupopapa.cfp.controller;

import br.com.grupopapa.cfp.dto.CatalogoProdutosResponse;
import br.com.grupopapa.cfp.security.UsuarioLogado;
import br.com.grupopapa.cfp.service.CatalogoProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/produtos/catalogo")
@Tag(name = "Produtos")
public class CatalogoProdutoController {

    private final CatalogoProdutoService service;

    public CatalogoProdutoController(CatalogoProdutoService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Todos os produtos do fornecedor: WinThor (antigos) + novos do portal")
    public CatalogoProdutosResponse catalogo(@RequestParam(required = false) Long fornecedorId) {
        return service.catalogo(UsuarioLogado.obter(), fornecedorId);
    }
}
