package br.com.grupopapa.cfp.controller;

import br.com.grupopapa.cfp.dto.ProdutoErpRequest;
import br.com.grupopapa.cfp.dto.ProdutoErpResponse;
import br.com.grupopapa.cfp.security.UsuarioLogado;
import br.com.grupopapa.cfp.service.IntegracaoErpService;
import br.com.grupopapa.cfp.service.ProdutoErpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/produtos/{produtoId}/erp")
@Tag(name = "Admin - Complemento WinThor")
public class AdminProdutoErpController {

    private final ProdutoErpService service;
    private final IntegracaoErpService integracaoService;

    public AdminProdutoErpController(ProdutoErpService service,
                                     IntegracaoErpService integracaoService) {
        this.service = service;
        this.integracaoService = integracaoService;
    }

    @PostMapping("/integrar")
    @Operation(summary = "Grava somente este produto no WinThor")
    public IntegracaoErpService.ResultadoItem integrar(@PathVariable Long produtoId) {
        return integracaoService.integrarProduto(produtoId, UsuarioLogado.obter());
    }

    @GetMapping
    @Operation(summary = "Campos do WinThor do produto (ou a sugestao inicial, se ainda nao houver)")
    public ProdutoErpResponse buscar(@PathVariable Long produtoId) {
        ProdutoErpService.Resultado r = service.buscar(produtoId, UsuarioLogado.obter());
        return ProdutoErpResponse.de(r.erp(), r.salvo());
    }

    @PutMapping
    @Operation(summary = "Grava os campos do WinThor do produto")
    public ProdutoErpResponse salvar(@PathVariable Long produtoId,
                                     @RequestBody @Valid ProdutoErpRequest request) {
        ProdutoErpService.Resultado r = service.salvar(produtoId, request, UsuarioLogado.obter());
        return ProdutoErpResponse.de(r.erp(), r.salvo());
    }
}
