package br.com.grupopapa.cfp.controller;

import br.com.grupopapa.cfp.domain.Produto;
import br.com.grupopapa.cfp.domain.StatusProduto;
import br.com.grupopapa.cfp.dto.*;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import br.com.grupopapa.cfp.security.UsuarioLogado;
import br.com.grupopapa.cfp.service.ExportacaoService;
import br.com.grupopapa.cfp.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/produtos")
@Tag(name = "Produtos")
public class ProdutoController {

    private final ProdutoService produtoService;
    private final ExportacaoService exportacaoService;

    public ProdutoController(ProdutoService produtoService, ExportacaoService exportacaoService) {
        this.produtoService = produtoService;
        this.exportacaoService = exportacaoService;
    }

    @GetMapping
    @Operation(summary = "Lista os produtos do fornecedor logado (admin ve todos)")
    public PaginaResponse<ProdutoResponse> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) StatusProduto status,
            @RequestParam(required = false) Long fornecedorId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho,
            @RequestParam(defaultValue = "dataAtualizacao") String ordenarPor,
            @RequestParam(defaultValue = "DESC") Sort.Direction direcao) {

        UsuarioAutenticado logado = UsuarioLogado.obter();
        Long escopo = logado.isAdmin() ? fornecedorId : logado.getFornecedorId();
        Sort ordem = Sort.by(direcao, ordenarPor).and(Sort.by(Sort.Direction.DESC, "id"));
        Page<Produto> page = produtoService.listar(escopo, status, termo,
                PageRequest.of(pagina, Math.min(tamanho, 200), ordem));
        return PaginaResponse.de(page, ProdutoResponse::de);
    }

    @GetMapping("/resumo")
    @Operation(summary = "Totais por status")
    public ResumoResponse resumo() {
        return produtoService.resumo(UsuarioLogado.obter());
    }

    @GetMapping("/{id}")
    public ProdutoResponse buscar(@PathVariable Long id) {
        return ProdutoResponse.de(produtoService.buscar(id, UsuarioLogado.obter()));
    }

    @GetMapping("/{id}/historico")
    @Operation(summary = "Historico de alteracoes e envios do produto")
    public List<HistoricoResponse> historico(@PathVariable Long id) {
        return produtoService.historico(id, UsuarioLogado.obter())
                .stream().map(HistoricoResponse::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProdutoResponse criar(@RequestBody @Valid ProdutoRequest request,
                                 @RequestParam(required = false) Long fornecedorId,
                                 @RequestParam(required = false) Long pedidoId) {
        return ProdutoResponse.de(
                produtoService.criar(request, UsuarioLogado.obter(), fornecedorId, pedidoId));
    }

    @PutMapping("/{id}")
    public ProdutoResponse atualizar(@PathVariable Long id, @RequestBody @Valid ProdutoRequest request) {
        return ProdutoResponse.de(produtoService.atualizar(id, request, UsuarioLogado.obter()));
    }

    @PostMapping("/{id}/enviar")
    @Operation(summary = "Envia o cadastro para analise da empresa")
    public ProdutoResponse enviar(@PathVariable Long id) {
        return ProdutoResponse.de(produtoService.enviar(id, UsuarioLogado.obter()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        produtoService.excluir(id, UsuarioLogado.obter());
    }

    @GetMapping("/exportar")
    @Operation(summary = "Exporta os produtos em Excel (xlsx) ou CSV")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) StatusProduto status,
            @RequestParam(required = false) Long fornecedorId) {

        UsuarioAutenticado logado = UsuarioLogado.obter();
        Long escopo = logado.isAdmin() ? fornecedorId : logado.getFornecedorId();
        List<Produto> produtos = produtoService.listarParaExportacao(escopo, status);

        boolean csv = "csv".equalsIgnoreCase(formato);
        byte[] conteudo = csv ? exportacaoService.gerarCsv(produtos) : exportacaoService.gerarExcel(produtos);
        String nome = "produtos." + (csv ? "csv" : "xlsx");
        MediaType tipo = csv ? MediaType.parseMediaType("text/csv; charset=UTF-8")
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .contentType(tipo)
                .body(conteudo);
    }
}
