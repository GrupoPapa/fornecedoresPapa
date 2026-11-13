package br.com.grupopapa.cfp.controller;

import br.com.grupopapa.cfp.domain.Fornecedor;
import br.com.grupopapa.cfp.dto.ConferenciaCodigosResponse;
import br.com.grupopapa.cfp.exception.NegocioException;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import br.com.grupopapa.cfp.security.UsuarioLogado;
import br.com.grupopapa.cfp.service.ConferenciaCodigosService;
import br.com.grupopapa.cfp.service.FornecedorService;
import br.com.grupopapa.cfp.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/** Conferencia "ao digitar" de EAN, DUN e codigo de fabrica, para a tela avisar antes de salvar. */
@RestController
@RequestMapping("/api/produtos/conferir-codigos")
@Tag(name = "Produtos")
public class ConferenciaCodigosController {

    private final ConferenciaCodigosService service;
    private final FornecedorService fornecedorService;
    private final ProdutoService produtoService;

    public ConferenciaCodigosController(ConferenciaCodigosService service, FornecedorService fornecedorService,
                                        ProdutoService produtoService) {
        this.service = service;
        this.fornecedorService = fornecedorService;
        this.produtoService = produtoService;
    }

    @GetMapping
    @Operation(summary = "Confere EAN, DUN e codigo de fabrica no portal e no WinThor")
    public ConferenciaCodigosResponse conferir(@RequestParam(required = false) String codigo,
                                               @RequestParam(required = false) String ean,
                                               @RequestParam(required = false) String dun,
                                               @RequestParam(required = false) Long produtoId,
                                               @RequestParam(required = false) Long fornecedorId) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        Fornecedor fornecedor;
        if (produtoId != null) {
            fornecedor = produtoService.buscar(produtoId, logado).getFornecedor(); // valida o acesso
        } else if (!logado.isAdmin()) {
            fornecedor = fornecedorService.buscar(logado.getFornecedorId());
        } else if (fornecedorId != null) {
            fornecedor = fornecedorService.buscar(fornecedorId);
        } else {
            throw new NegocioException("Informe o fornecedor.");
        }
        return service.conferir(fornecedor, codigo, ean, dun, produtoId);
    }
}
