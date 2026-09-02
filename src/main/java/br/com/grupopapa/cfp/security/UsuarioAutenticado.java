package br.com.grupopapa.cfp.security;

import br.com.grupopapa.cfp.domain.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UsuarioAutenticado implements UserDetails {

    private final Usuario usuario;

    public UsuarioAutenticado(Usuario usuario) {
        this.usuario = usuario;
    }

    public Usuario getUsuario() { return usuario; }

    public Long getId() { return usuario.getId(); }

    public Long getFornecedorId() {
        return usuario.getFornecedor() != null ? usuario.getFornecedor().getId() : null;
    }

    public boolean isAdmin() { return usuario.isAdmin(); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name()));
    }

    @Override public String getPassword() { return usuario.getSenha(); }
    @Override public String getUsername() { return usuario.getEmail(); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return Boolean.TRUE.equals(usuario.getAtivo()); }
}
