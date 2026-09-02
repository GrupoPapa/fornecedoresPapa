package br.com.grupopapa.cfp.controller;

import br.com.grupopapa.cfp.dto.DefinirSenhaRequest;
import br.com.grupopapa.cfp.dto.PaginaResponse;
import br.com.grupopapa.cfp.dto.SenhaGeradaResponse;
import br.com.grupopapa.cfp.dto.UsuarioRequest;
import br.com.grupopapa.cfp.dto.UsuarioResponse;
import br.com.grupopapa.cfp.security.UsuarioLogado;
import br.com.grupopapa.cfp.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/usuarios")
@Tag(name = "Admin - Usuarios")
public class AdminUsuarioController {

    private final UsuarioService usuarioService;

    public AdminUsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public PaginaResponse<UsuarioResponse> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return PaginaResponse.de(
                usuarioService.listar(termo, PageRequest.of(pagina, Math.min(tamanho, 200))),
                UsuarioResponse::de);
    }

    @GetMapping("/{id}")
    public UsuarioResponse buscar(@PathVariable Long id) {
        return UsuarioResponse.de(usuarioService.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria o acesso do fornecedor e devolve a senha provisoria (exibida uma unica vez)")
    public SenhaGeradaResponse criar(@RequestBody @Valid UsuarioRequest request) {
        UsuarioService.ResultadoCriacao r = usuarioService.criar(request);
        return montarResposta(r);
    }

    @PutMapping("/{id}")
    public UsuarioResponse atualizar(@PathVariable Long id, @RequestBody @Valid UsuarioRequest request) {
        return UsuarioResponse.de(usuarioService.atualizar(id, request));
    }

    @PostMapping("/{id}/senha")
    @Operation(summary = "Define a senha do usuario (em branco, o sistema gera uma)")
    public SenhaGeradaResponse definirSenha(@PathVariable Long id,
                                            @RequestBody(required = false) @Valid DefinirSenhaRequest request) {
        UsuarioService.ResultadoCriacao r =
                usuarioService.definirSenha(id, request == null ? null : request.senha());
        return montarResposta(r);
    }

    private SenhaGeradaResponse montarResposta(UsuarioService.ResultadoCriacao r) {
        Long codigo = r.usuario().getFornecedor() != null
                ? r.usuario().getFornecedor().getCodFornecedorErp() : null;
        return new SenhaGeradaResponse(r.usuario().getId(), r.usuario().getEmail(),
                codigo, r.senhaProvisoria());
    }

    @PatchMapping("/{id}/situacao")
    @Operation(summary = "Ativa ou inativa o usuario")
    public UsuarioResponse situacao(@PathVariable Long id, @RequestParam boolean ativo) {
        return UsuarioResponse.de(
                usuarioService.alterarSituacao(id, ativo, UsuarioLogado.obter().getId()));
    }
}
