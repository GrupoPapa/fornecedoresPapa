package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.Fornecedor;
import br.com.grupopapa.cfp.domain.Perfil;
import br.com.grupopapa.cfp.domain.Usuario;
import br.com.grupopapa.cfp.dto.UsuarioRequest;
import br.com.grupopapa.cfp.exception.NegocioException;
import br.com.grupopapa.cfp.exception.RecursoNaoEncontradoException;
import br.com.grupopapa.cfp.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
public class UsuarioService {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository repository;
    private final FornecedorService fornecedorService;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository repository, FornecedorService fornecedorService,
                          PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.fornecedorService = fornecedorService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Page<Usuario> listar(String termo, Pageable pageable) {
        return repository.buscar((termo == null || termo.isBlank()) ? null : termo.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public Usuario buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario nao encontrado."));
    }

    /** Cria o usuario e devolve a senha provisoria em texto puro (mostrada uma unica vez ao admin). */
    @Transactional
    public ResultadoCriacao criar(UsuarioRequest request) {
        String email = request.email().trim().toLowerCase();
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new NegocioException("Ja existe um usuario com este e-mail.");
        }
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setNome(request.nome().trim());
        u.setPerfil(request.perfil());
        u.setAtivo(request.ativo() == null || request.ativo());
        u.setTrocarSenha(false); // a senha e definida pela empresa; o fornecedor nao troca
        vincularFornecedor(u, request);

        String senha = (request.senhaProvisoria() == null || request.senhaProvisoria().isBlank())
                ? gerarSenha() : request.senhaProvisoria();
        u.setSenha(passwordEncoder.encode(senha));

        return new ResultadoCriacao(repository.save(u), senha);
    }

    @Transactional
    public Usuario atualizar(Long id, UsuarioRequest request) {
        Usuario u = buscar(id);
        String email = request.email().trim().toLowerCase();
        if (!u.getEmail().equalsIgnoreCase(email) && repository.existsByEmailIgnoreCase(email)) {
            throw new NegocioException("Ja existe um usuario com este e-mail.");
        }
        u.setEmail(email);
        u.setNome(request.nome().trim());
        u.setPerfil(request.perfil());
        u.setAtivo(request.ativo() == null || request.ativo());
        vincularFornecedor(u, request);
        return repository.save(u);
    }

    /**
     * Define a senha do usuario. Se {@code novaSenha} vier vazia, o sistema gera uma.
     * A senha vale ate que a empresa troque - o fornecedor nao precisa alterar no acesso.
     */
    @Transactional
    public ResultadoCriacao definirSenha(Long id, String novaSenha) {
        Usuario u = buscar(id);
        String senha = (novaSenha == null || novaSenha.isBlank()) ? gerarSenha() : novaSenha.trim();
        if (senha.length() < 8) {
            throw new NegocioException("A senha deve ter no minimo 8 caracteres.");
        }
        u.setSenha(passwordEncoder.encode(senha));
        u.setTrocarSenha(false);
        return new ResultadoCriacao(repository.save(u), senha);
    }

    @Transactional
    public Usuario alterarSituacao(Long id, boolean ativo, Long usuarioLogadoId) {
        Usuario u = buscar(id);
        if (u.getId().equals(usuarioLogadoId) && !ativo) {
            throw new NegocioException("Voce nao pode inativar o proprio usuario.");
        }
        u.setAtivo(ativo);
        return repository.save(u);
    }

    private void vincularFornecedor(Usuario u, UsuarioRequest request) {
        if (request.perfil() == Perfil.FORNECEDOR) {
            if (request.fornecedorId() == null) {
                throw new NegocioException("Selecione o fornecedor do usuario.");
            }
            Fornecedor fornecedor = fornecedorService.buscar(request.fornecedorId());
            u.setFornecedor(fornecedor);
        } else {
            u.setFornecedor(null);
        }
    }

    private String gerarSenha() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    public record ResultadoCriacao(Usuario usuario, String senhaProvisoria) { }
}
