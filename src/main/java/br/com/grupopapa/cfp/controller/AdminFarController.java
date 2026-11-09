package br.com.grupopapa.cfp.controller;

import br.com.grupopapa.cfp.dto.*;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import br.com.grupopapa.cfp.security.UsuarioLogado;
import br.com.grupopapa.cfp.service.FarService;
import br.com.grupopapa.cfp.service.FornecedorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Equipe interna: motivos, fornecedores liberados e vinculo com a baixa fiscal. Exige ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/far")
@Tag(name = "Admin - FAR")
public class AdminFarController {

    private final FarService service;
    private final FornecedorService fornecedorService;

    public AdminFarController(FarService service, FornecedorService fornecedorService) {
        this.service = service;
        this.fornecedorService = fornecedorService;
    }

    @GetMapping("/fornecedores")
    @Operation(summary = "Fornecedores com o controle de avarias liberado")
    public List<FornecedorResponse> fornecedores() {
        return fornecedorService.listarTodos().stream()
                .filter(f -> f.isFarHabilitado())
                .sorted(Comparator.comparing(f -> f.getRazaoSocial()))
                .map(FornecedorResponse::de)
                .toList();
    }

    @GetMapping("/motivos")
    @Operation(summary = "Todos os motivos, inclusive inativos")
    public List<FarMotivoResponse> motivos() {
        return service.todosMotivos().stream().map(FarMotivoResponse::de).toList();
    }

    @PostMapping("/motivos")
    @ResponseStatus(HttpStatus.CREATED)
    public FarMotivoResponse criarMotivo(@RequestBody @Valid FarMotivoRequest request) {
        return FarMotivoResponse.de(service.salvarMotivo(null, request));
    }

    @PutMapping("/motivos/{id}")
    public FarMotivoResponse atualizarMotivo(@PathVariable Long id, @RequestBody @Valid FarMotivoRequest request) {
        return FarMotivoResponse.de(service.salvarMotivo(id, request));
    }

    @PostMapping("/{id}/baixa-fiscal")
    @Operation(summary = "Registra que a FAR foi vinculada ao processo de baixa fiscal da avaria no sistema")
    public FarResponse vincularBaixa(@PathVariable Long id,
                                     @RequestBody(required = false) @Valid BaixaFiscalRequest request) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        service.vincularBaixaFiscal(id, request, logado);
        return service.resposta(service.buscar(id, logado), logado);
    }

    @PostMapping("/{id}/baixa-fiscal/desfazer")
    @Operation(summary = "Desfaz o vinculo com a baixa fiscal (libera a FAR para o fornecedor de novo)")
    public FarResponse desfazerBaixa(@PathVariable Long id,
                                     @RequestBody(required = false) Map<String, String> corpo) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        service.desfazerBaixaFiscal(id, corpo == null ? null : corpo.get("motivo"), logado);
        return service.resposta(service.buscar(id, logado), logado);
    }
}
