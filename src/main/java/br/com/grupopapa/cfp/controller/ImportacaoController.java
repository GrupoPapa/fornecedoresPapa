package br.com.grupopapa.cfp.controller;

import br.com.grupopapa.cfp.importacao.ImportacaoCompraService;
import br.com.grupopapa.cfp.importacao.ImportacaoProdutoService;
import br.com.grupopapa.cfp.importacao.ImportacaoResultado;
import br.com.grupopapa.cfp.security.UsuarioLogado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Planilhas modelo para o fornecedor preencher fora do portal, e a importacao do arquivo
 * preenchido. O fornecedor usa para si; a equipe interna informa o fornecedorId.
 */
@RestController
@RequestMapping("/api/importacao")
@Tag(name = "Importacao por planilha")
public class ImportacaoController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ImportacaoProdutoService produtos;
    private final ImportacaoCompraService compras;

    public ImportacaoController(ImportacaoProdutoService produtos, ImportacaoCompraService compras) {
        this.produtos = produtos;
        this.compras = compras;
    }

    @GetMapping("/produtos/modelo")
    @Operation(summary = "Planilha modelo do cadastro de produtos (colunas conforme o tipo do fornecedor)")
    public ResponseEntity<byte[]> modeloProdutos(@RequestParam(required = false) Long fornecedorId) {
        return arquivo(produtos.modelo(UsuarioLogado.obter(), fornecedorId), "modelo-cadastro-produtos.xlsx");
    }

    @PostMapping(value = "/produtos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importa a planilha de produtos para o pedido de cadastro em aberto")
    public ImportacaoResultado importarProdutos(@RequestParam("arquivo") MultipartFile arquivo,
                                                @RequestParam(required = false) Long fornecedorId) {
        return produtos.importar(arquivo, UsuarioLogado.obter(), fornecedorId);
    }

    @GetMapping("/pedidos-compra/modelo")
    @Operation(summary = "Planilha modelo do pedido de compra, com a lista de produtos do fornecedor")
    public ResponseEntity<byte[]> modeloCompra(@RequestParam(required = false) Long fornecedorId) {
        return arquivo(compras.modelo(UsuarioLogado.obter(), fornecedorId), "modelo-pedido-compra.xlsx");
    }

    @PostMapping(value = "/pedidos-compra", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importa a planilha e cria o pedido de compra em digitacao")
    public ImportacaoResultado importarCompra(@RequestParam("arquivo") MultipartFile arquivo,
                                              @RequestParam(required = false) Long fornecedorId) {
        return compras.importar(arquivo, UsuarioLogado.obter(), fornecedorId);
    }

    private ResponseEntity<byte[]> arquivo(byte[] conteudo, String nome) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .contentType(XLSX)
                .body(conteudo);
    }
}
