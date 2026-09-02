package br.com.grupopapa.cfp.security;

import br.com.grupopapa.cfp.domain.Usuario;
import br.com.grupopapa.cfp.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario ou senha invalidos"));
        // forca a carga do fornecedor dentro da transacao (associacao LAZY)
        if (usuario.getFornecedor() != null) {
            usuario.getFornecedor().getRazaoSocial();
        }
        return new UsuarioAutenticado(usuario);
    }
}
