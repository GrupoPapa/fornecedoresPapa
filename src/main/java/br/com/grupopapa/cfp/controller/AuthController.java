package br.com.grupopapa.cfp.controller;

import br.com.grupopapa.cfp.dto.*;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import br.com.grupopapa.cfp.security.UsuarioLogado;
import br.com.grupopapa.cfp.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticacao")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica o usuario e devolve o token JWT")
    public LoginResponse login(@RequestBody @Valid LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/trocar-senha")
    @Operation(summary = "Troca a senha do usuario autenticado")
    public ResponseEntity<Void> trocarSenha(@RequestBody @Valid TrocarSenhaRequest request) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        authService.trocarSenha(logado.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Dados do usuario autenticado")
    public UsuarioResponse me() {
        return UsuarioResponse.de(UsuarioLogado.obter().getUsuario());
    }
}
