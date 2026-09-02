package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.Produto;
import br.com.grupopapa.cfp.domain.ProdutoFoto;
import br.com.grupopapa.cfp.dto.FotoResponse;
import br.com.grupopapa.cfp.exception.NegocioException;
import br.com.grupopapa.cfp.exception.RecursoNaoEncontradoException;
import br.com.grupopapa.cfp.repository.ProdutoFotoRepository;
import br.com.grupopapa.cfp.repository.UsuarioRepository;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Service
public class ProdutoFotoService {

    private static final Set<String> TIPOS_ACEITOS =
            Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");

    private final ProdutoFotoRepository fotoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProdutoService produtoService;
    private final int maxPorProduto;
    private final long maxBytes;

    public ProdutoFotoService(ProdutoFotoRepository fotoRepository,
                              UsuarioRepository usuarioRepository,
                              ProdutoService produtoService,
                              @Value("${app.fotos.max-por-produto:5}") int maxPorProduto,
                              @Value("${app.fotos.max-mb:5}") long maxMb) {
        this.fotoRepository = fotoRepository;
        this.usuarioRepository = usuarioRepository;
        this.produtoService = produtoService;
        this.maxPorProduto = maxPorProduto;
        this.maxBytes = maxMb * 1024L * 1024L;
    }

    @Transactional(readOnly = true)
    public List<FotoResponse> listar(Long produtoId, UsuarioAutenticado logado) {
        produtoService.buscar(produtoId, logado); // valida o acesso ao produto
        return fotoRepository.listarPorProduto(produtoId);
    }

    @Transactional
    public FotoResponse anexar(Long produtoId, MultipartFile arquivo, UsuarioAutenticado logado) {
        Produto produto = produtoService.buscar(produtoId, logado);
        if (!logado.isAdmin() && !produto.isEditavelPeloFornecedor()) {
            throw new NegocioException("Produto ja enviado nao aceita novas fotos. "
                    + "Solicite a reabertura ao time de cadastro.");
        }
        if (arquivo == null || arquivo.isEmpty()) {
            throw new NegocioException("Selecione uma imagem.");
        }
        if (arquivo.getSize() > maxBytes) {
            throw new NegocioException("A imagem tem " + (arquivo.getSize() / 1024 / 1024)
                    + " MB. O limite por foto e de " + (maxBytes / 1024 / 1024) + " MB.");
        }
        String tipo = arquivo.getContentType() == null ? "" : arquivo.getContentType().toLowerCase();
        if (!TIPOS_ACEITOS.contains(tipo)) {
            throw new NegocioException("Formato nao aceito. Envie a foto em JPG, PNG ou WEBP.");
        }
        long existentes = fotoRepository.countByProdutoId(produtoId);
        if (existentes >= maxPorProduto) {
            throw new NegocioException("Este produto ja tem " + maxPorProduto
                    + " fotos. Exclua uma antes de anexar outra.");
        }

        ProdutoFoto foto = new ProdutoFoto();
        foto.setProduto(produto);
        foto.setNomeArquivo(nomeSeguro(arquivo.getOriginalFilename()));
        foto.setTipoConteudo(tipo);
        foto.setTamanho((int) arquivo.getSize());
        foto.setPrincipal(existentes == 0); // a primeira vira a foto principal
        foto.setUsuario(usuarioRepository.getReferenceById(logado.getId()));
        try {
            foto.setConteudo(arquivo.getBytes());
        } catch (IOException e) {
            throw new NegocioException("Nao foi possivel ler a imagem enviada.");
        }

        ProdutoFoto salva = fotoRepository.save(foto);
        return new FotoResponse(salva.getId(), salva.getNomeArquivo(), salva.getTipoConteudo(),
                salva.getTamanho(), salva.getPrincipal(), salva.getDataCriacao());
    }

    @Transactional(readOnly = true)
    public ProdutoFoto baixar(Long produtoId, Long fotoId, UsuarioAutenticado logado) {
        produtoService.buscar(produtoId, logado);
        ProdutoFoto foto = fotoRepository.findByIdAndProdutoId(fotoId, produtoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Foto nao encontrada."));
        foto.getConteudo().getClass(); // garante a carga do BLOB dentro da transacao
        return foto;
    }

    @Transactional
    public void excluir(Long produtoId, Long fotoId, UsuarioAutenticado logado) {
        Produto produto = produtoService.buscar(produtoId, logado);
        if (!logado.isAdmin() && !produto.isEditavelPeloFornecedor()) {
            throw new NegocioException("Produto ja enviado nao permite excluir fotos.");
        }
        ProdutoFoto foto = fotoRepository.findByIdAndProdutoId(fotoId, produtoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Foto nao encontrada."));
        boolean eraPrincipal = Boolean.TRUE.equals(foto.getPrincipal());
        fotoRepository.delete(foto);

        if (eraPrincipal) {
            fotoRepository.findByProdutoId(produtoId).stream().findFirst().ifPresent(nova -> {
                nova.setPrincipal(true);
                fotoRepository.save(nova);
            });
        }
    }

    @Transactional
    public void definirPrincipal(Long produtoId, Long fotoId, UsuarioAutenticado logado) {
        produtoService.buscar(produtoId, logado);
        List<ProdutoFoto> fotos = fotoRepository.findByProdutoId(produtoId);
        boolean achou = false;
        for (ProdutoFoto f : fotos) {
            boolean principal = f.getId().equals(fotoId);
            achou = achou || principal;
            f.setPrincipal(principal);
        }
        if (!achou) {
            throw new RecursoNaoEncontradoException("Foto nao encontrada.");
        }
        fotoRepository.saveAll(fotos);
    }

    @Transactional
    public void excluirDoProduto(Long produtoId) {
        fotoRepository.deleteByProdutoId(produtoId);
    }

    private String nomeSeguro(String nome) {
        if (nome == null || nome.isBlank()) {
            return "foto";
        }
        String limpo = nome.replaceAll("[\\\\/\\r\\n]", "_").trim();
        return limpo.length() > 200 ? limpo.substring(limpo.length() - 200) : limpo;
    }
}
