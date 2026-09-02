package br.com.grupopapa.cfp.security;

import br.com.grupopapa.cfp.domain.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey chave;
    private final long expiracaoMillis;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiracao-minutos:480}") long expiracaoMinutos) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret precisa ter no minimo 32 caracteres");
        }
        this.chave = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiracaoMillis = expiracaoMinutos * 60_000L;
    }

    public String gerarToken(Usuario usuario) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", usuario.getId());
        claims.put("nome", usuario.getNome());
        claims.put("perfil", usuario.getPerfil().name());
        if (usuario.getFornecedor() != null) {
            claims.put("fornecedorId", usuario.getFornecedor().getId());
        }
        Date agora = new Date();
        return Jwts.builder()
                .claims(claims)
                .subject(usuario.getEmail())
                .issuedAt(agora)
                .expiration(new Date(agora.getTime() + expiracaoMillis))
                .signWith(chave)
                .compact();
    }

    public String extrairEmail(String token) {
        return lerClaims(token).getSubject();
    }

    public boolean valido(String token) {
        try {
            lerClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getExpiracaoMillis() {
        return expiracaoMillis;
    }

    private Claims lerClaims(String token) {
        return Jwts.parser().verifyWith(chave).build().parseSignedClaims(token).getPayload();
    }
}
