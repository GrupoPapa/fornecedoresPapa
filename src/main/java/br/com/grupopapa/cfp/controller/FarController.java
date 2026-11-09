package br.com.grupopapa.cfp.controller;

import br.com.grupopapa.cfp.domain.Far;
import br.com.grupopapa.cfp.domain.FarAnexo;
import br.com.grupopapa.cfp.domain.StatusFar;
import br.com.grupopapa.cfp.domain.TipoAnexoFar;
import br.com.grupopapa.cfp.dto.*;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import br.com.grupopapa.cfp.security.UsuarioLogado;
import br.com.grupopapa.cfp.service.ExportacaoFarService;
import br.com.grupopapa.cfp.service.FarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * Controle de avarias (FAR). Disponivel apenas para os fornecedores liberados
 * (CFP_FORNECEDOR.HABILITA_FAR) e para a equipe interna.
 */
@RestController
@RequestMapping("/api/far")
@Tag(name = "FAR - controle de avarias")
public class FarController {

    private final FarService service;
    private final ExportacaoFarService exportacaoService;

    public FarController(FarService service, ExportacaoFarService exportacaoService) {
        this.service = service;
        this.exportacaoService = exportacaoService;
    }

    @GetMapping
    @Operation(summary = "FARs do fornecedor logado (admin ve todas)")
    public PaginaResponse<FarResponse> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) StatusFar status,
            @RequestParam(required = false) Long motivoId,
            @RequestParam(required = false) Boolean baixaFiscal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataAte,
            @RequestParam(required = false) Long fornecedorId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        var page = service.listar(logado, fornecedorId, status, motivoId, baixaFiscal, dataDe, dataAte, termo,
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 200),
                        Sort.by(Sort.Direction.DESC, "dataVisita").and(Sort.by(Sort.Direction.DESC, "id"))));
        return service.respostaPagina(page, logado);
    }

    @GetMapping("/motivos")
    @Operation(summary = "Motivos de avaria disponiveis")
    public List<FarMotivoResponse> motivos() {
        return service.motivosAtivos(UsuarioLogado.obter()).stream().map(FarMotivoResponse::de).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra a FAR (parte 'dados' em JSON + comprovante opcional)")
    public FarResponse criar(@RequestPart("dados") @Valid FarRequest dados,
                             @RequestPart(value = "comprovante", required = false) MultipartFile comprovante,
                             @RequestParam(required = false) Long fornecedorId) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        Far far = service.criar(dados, comprovante, logado, fornecedorId);
        return service.resposta(service.buscar(far.getId(), logado), logado);
    }

    @GetMapping("/{id}")
    public FarResponse buscar(@PathVariable Long id) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        return service.resposta(service.buscar(id, logado), logado);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Atualiza a FAR (parte 'dados' em JSON + comprovante opcional)")
    public FarResponse atualizar(@PathVariable Long id,
                                 @RequestPart("dados") @Valid FarRequest dados,
                                 @RequestPart(value = "comprovante", required = false) MultipartFile comprovante) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        service.atualizar(id, dados, comprovante, logado);
        return service.resposta(service.buscar(id, logado), logado);
    }

    @GetMapping("/{id}/historico")
    public List<HistoricoFarResponse> historico(@PathVariable Long id) {
        return service.historico(id, UsuarioLogado.obter()).stream().map(HistoricoFarResponse::de).toList();
    }

    // ------------------------------------------------------------ anexos

    @GetMapping("/{id}/anexos")
    public List<FarAnexoResponse> anexos(@PathVariable Long id) {
        return service.listarAnexos(id, UsuarioLogado.obter());
    }

    @PostMapping(value = "/{id}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Anexa um arquivo (PDF, JPG, PNG ou WEBP)")
    public FarAnexoResponse anexar(@PathVariable Long id,
                                   @RequestParam("arquivo") MultipartFile arquivo,
                                   @RequestParam(defaultValue = "OUTRO") TipoAnexoFar tipo) {
        return service.anexar(id, arquivo, tipo, UsuarioLogado.obter());
    }

    @GetMapping("/{id}/anexos/{anexoId}")
    @Operation(summary = "Baixa o anexo")
    public ResponseEntity<byte[]> baixarAnexo(@PathVariable Long id, @PathVariable Long anexoId) {
        FarAnexo anexo = service.baixarAnexo(id, anexoId, UsuarioLogado.obter());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(anexo.getTipoConteudo()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(anexo.getNomeArquivo(),
                                java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noCache())
                .body(anexo.getConteudo());
    }

    @DeleteMapping("/{id}/anexos/{anexoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirAnexo(@PathVariable Long id, @PathVariable Long anexoId) {
        service.excluirAnexo(id, anexoId, UsuarioLogado.obter());
    }

    // ------------------------------------------------------------ exportacao

    @GetMapping("/exportar")
    @Operation(summary = "Exporta o controle de avarias em xlsx ou csv")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "xlsx") String formato,
            @RequestParam(required = false) StatusFar status,
            @RequestParam(required = false) Long motivoId,
            @RequestParam(required = false) Boolean baixaFiscal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataAte,
            @RequestParam(required = false) Long fornecedorId) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        List<Far> fars = service.listarParaExportacao(logado, fornecedorId, status, motivoId, baixaFiscal, dataDe, dataAte);
        var comprovantes = service.comprovantesPorFar(fars.stream().map(Far::getId).toList());
        boolean csv = "csv".equalsIgnoreCase(formato);
        byte[] conteudo = csv ? exportacaoService.gerarCsv(fars, comprovantes)
                : exportacaoService.gerarExcel(fars, comprovantes);
        MediaType tipo = csv ? MediaType.parseMediaType("text/csv; charset=UTF-8")
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"controle-avarias." + (csv ? "csv" : "xlsx") + "\"")
                .contentType(tipo)
                .body(conteudo);
    }
}
