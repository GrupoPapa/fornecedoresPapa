package br.com.grupopapa.cfp.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Atalho para recuperar o usuario autenticado no contexto atual. */
public final class UsuarioLogado {

    private UsuarioLogado() { }

    public static UsuarioAutenticado obter() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado ua)) {
            throw new IllegalStateException("Nenhum usuario autenticado no contexto");
        }
        return ua;
    }
}
