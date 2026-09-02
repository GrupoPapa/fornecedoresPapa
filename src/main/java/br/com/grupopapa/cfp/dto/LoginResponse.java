package br.com.grupopapa.cfp.dto;

public record LoginResponse(String token, long expiraEmMs, UsuarioResponse usuario) {
}
