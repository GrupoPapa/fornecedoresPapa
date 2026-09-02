package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.Fornecedor;
import br.com.grupopapa.cfp.domain.Usuario;
import br.com.grupopapa.cfp.dto.LoginRequest;
import br.com.grupopapa.cfp.dto.LoginResponse;
import br.com.grupopapa.cfp.dto.TrocarSenhaRequest;
import br.com.grupopapa.cfp.dto.UsuarioResponse;
import br.com.grupopapa.cfp.exception.NegocioException;
import br.com.grupopapa.cfp.repository.FornecedorRepository;
import br.com.grupopapa.cfp.repository.UsuarioRepository;
import br.com.grupopapa.cfp.security.JwtService;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final FornecedorRepository fornecedorRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authenticationManager, UsuarioRepository usuarioRepository,
                       FornecedorRepository fornecedorRepository,
                       JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.fornecedorRepository = fornecedorRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * O fornecedor pode entrar com o codigo dele no WinThor no lugar do e-mail.
     * Resolve o identificador informado para o e-mail usado na autenticacao.
     */
    private String resolverLogin(String informado) {
        String valor = informado == null ? "" : informado.trim();
        if (!valor.matches("\\d+")) {
            return valor; // e-mail
        }

        Long codigo;
        try {
            codigo = Long.valueOf(valor);
        } catch (NumberFormatException e) {
            throw new NegocioException("Codigo de fornecedor invalido.");
        }

        Fornecedor fornecedor = fornecedorRepository.findByCodFornecedorErp(codigo)
                .orElseThrow(() -> new NegocioException(
                        "Nao encontramos o fornecedor de codigo " + codigo + "."));

        List<Usuario> usuarios = usuarioRepository.findByFornecedorIdOrderByNome(fornecedor.getId())
                .stream().filter(u -> Boolean.TRUE.equals(u.getAtivo())).toList();

        if (usuarios.isEmpty()) {
            throw new NegocioException("O fornecedor " + codigo
                    + " ainda nao tem acesso liberado. Procure a equipe de cadastro.");
        }
        if (usuarios.size() > 1) {
            throw new NegocioException("Este fornecedor tem mais de um usuario. Entre com o e-mail.");
        }
        return usuarios.get(0).getEmail();
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String login = resolverLogin(request.usuario());
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(login, request.senha()));

        UsuarioAutenticado autenticado = (UsuarioAutenticado) auth.getPrincipal();
        Usuario usuario = usuarioRepository.findById(autenticado.getId()).orElseThrow();

        if (usuario.getFornecedor() != null && !Boolean.TRUE.equals(usuario.getFornecedor().getAtivo())) {
            throw new NegocioException("Fornecedor inativo. Procure o administrador.");
        }

        usuario.setDataUltimoAcesso(OffsetDateTime.now());
        usuarioRepository.save(usuario);

        String token = jwtService.gerarToken(usuario);
        return new LoginResponse(token, jwtService.getExpiracaoMillis(), UsuarioResponse.de(usuario));
    }

    @Transactional
    public void trocarSenha(Long usuarioId, TrocarSenhaRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NegocioException("Usuario nao encontrado"));

        if (!passwordEncoder.matches(request.senhaAtual(), usuario.getSenha())) {
            throw new NegocioException("Senha atual incorreta.");
        }
        if (passwordEncoder.matches(request.novaSenha(), usuario.getSenha())) {
            throw new NegocioException("A nova senha deve ser diferente da atual.");
        }
        usuario.setSenha(passwordEncoder.encode(request.novaSenha()));
        usuario.setTrocarSenha(false);
        usuarioRepository.save(usuario);
    }
}
