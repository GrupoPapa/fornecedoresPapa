package br.com.grupopapa.cfp.controller;

import br.com.grupopapa.cfp.domain.ProdutoFoto;
import br.com.grupopapa.cfp.dto.FotoResponse;
import br.com.grupopapa.cfp.security.UsuarioLogado;
import br.com.grupopapa.cfp.service.ProdutoFotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/produtos/{produtoId}/fotos")
@Tag(name = "Fotos do produto")
public class ProdutoFotoController {

    private final ProdutoFotoService fotoService;

    public ProdutoFotoController(ProdutoFotoService fotoService) {
        this.fotoService = fotoService;
    }

    @GetMapping
    @Operation(summary = "Lista as fotos do produto (somente os dados, sem a imagem)")
    public List<FotoResponse> listar(@PathVariable Long produtoId) {
        return fotoService.listar(produtoId, UsuarioLogado.obter());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Anexa uma foto (JPG, PNG ou WEBP)")
    public FotoResponse anexar(@PathVariable Long produtoId,
                               @RequestParam("arquivo") MultipartFile arquivo) {
        return fotoService.anexar(produtoId, arquivo, UsuarioLogado.obter());
    }

    @GetMapping("/{fotoId}")
    @Operation(summary = "Baixa a imagem")
    public ResponseEntity<byte[]> baixar(@PathVariable Long produtoId, @PathVariable Long fotoId) {
        ProdutoFoto foto = fotoService.baixar(produtoId, fotoId, UsuarioLogado.obter());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.getTipoConteudo()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + foto.getNomeArquivo() + "\"")
                .cacheControl(CacheControl.noCache())
                .body(foto.getConteudo());
    }

    @PatchMapping("/{fotoId}/principal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Define a foto principal do produto")
    public void principal(@PathVariable Long produtoId, @PathVariable Long fotoId) {
        fotoService.definirPrincipal(produtoId, fotoId, UsuarioLogado.obter());
    }

    @DeleteMapping("/{fotoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long produtoId, @PathVariable Long fotoId) {
        fotoService.excluir(produtoId, fotoId, UsuarioLogado.obter());
    }
}
