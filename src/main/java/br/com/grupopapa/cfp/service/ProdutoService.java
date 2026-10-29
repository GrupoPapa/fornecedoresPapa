package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.*;
import br.com.grupopapa.cfp.dto.ProdutoRequest;
import br.com.grupopapa.cfp.dto.ResumoResponse;
import br.com.grupopapa.cfp.exception.NegocioException;
import br.com.grupopapa.cfp.exception.RecursoNaoEncontradoException;
import br.com.grupopapa.cfp.repository.ProdutoFotoRepository;
import br.com.grupopapa.cfp.repository.ProdutoHistoricoRepository;
import br.com.grupopapa.cfp.repository.ProdutoRepository;
import br.com.grupopapa.cfp.repository.UsuarioRepository;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final ProdutoHistoricoRepository historicoRepository;
    private final ProdutoFotoRepository fotoRepository;
    private final UsuarioRepository usuarioRepository;
    private final FornecedorService fornecedorService;
    private final PedidoService pedidoService;

    public ProdutoService(ProdutoRepository produtoRepository,
                          ProdutoHistoricoRepository historicoRepository,
                          ProdutoFotoRepository fotoRepository,
                          UsuarioRepository usuarioRepository,
                          FornecedorService fornecedorService,
                          PedidoService pedidoService) {
        this.pedidoService = pedidoService;
        this.produtoRepository = produtoRepository;
        this.historicoRepository = historicoRepository;
        this.fotoRepository = fotoRepository;
        this.usuarioRepository = usuarioRepository;
        this.fornecedorService = fornecedorService;
    }

    // ------------------------------------------------------------------ consulta

    @Transactional(readOnly = true)
    public Page<Produto> listar(Long fornecedorId, StatusProduto status, String termo, Pageable pageable) {
        return produtoRepository.pesquisar(fornecedorId, status,
                (termo == null || termo.isBlank()) ? null : termo.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public List<Produto> listarParaExportacao(Long fornecedorId, StatusProduto status) {
        return produtoRepository.listarParaExportacao(fornecedorId, status);
    }

    @Transactional(readOnly = true)
    public Produto buscar(Long id, UsuarioAutenticado logado) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto nao encontrado."));
        validarAcesso(produto, logado);
        return produto;
    }

    @Transactional(readOnly = true)
    public List<ProdutoHistorico> historico(Long produtoId, UsuarioAutenticado logado) {
        buscar(produtoId, logado);
        return historicoRepository.listarPorProduto(produtoId);
    }

    @Transactional(readOnly = true)
    public ResumoResponse resumo(UsuarioAutenticado logado) {
        Long fornecedorId = logado.isAdmin() ? null : logado.getFornecedorId();
        if (fornecedorId == null) {
            return new ResumoResponse(
                    produtoRepository.countByStatus(StatusProduto.RASCUNHO),
                    produtoRepository.countByStatus(StatusProduto.ENVIADO),
                    produtoRepository.countByStatus(StatusProduto.APROVADO),
                    produtoRepository.countByStatus(StatusProduto.REPROVADO));
        }
        return new ResumoResponse(
                produtoRepository.countByFornecedorIdAndStatus(fornecedorId, StatusProduto.RASCUNHO),
                produtoRepository.countByFornecedorIdAndStatus(fornecedorId, StatusProduto.ENVIADO),
                produtoRepository.countByFornecedorIdAndStatus(fornecedorId, StatusProduto.APROVADO),
                produtoRepository.countByFornecedorIdAndStatus(fornecedorId, StatusProduto.REPROVADO));
    }

    // ------------------------------------------------------------------ escrita

    @Transactional
    public Produto criar(ProdutoRequest request, UsuarioAutenticado logado,
                         Long fornecedorIdInformado, Long pedidoId) {
        Pedido pedido = null;
        Fornecedor fornecedor;

        if (pedidoId != null) {
            pedido = pedidoService.buscar(pedidoId, logado);
            if (!pedido.isEditavelPeloFornecedor()) {
                throw new NegocioException("O pedido " + pedido.getNumero()
                        + " ja foi enviado e nao aceita novos produtos.");
            }
            fornecedor = pedido.getFornecedor();
        } else {
            Long fornecedorId = logado.isAdmin() ? fornecedorIdInformado : logado.getFornecedorId();
            if (fornecedorId == null) {
                throw new NegocioException("Informe o fornecedor do produto.");
            }
            fornecedor = fornecedorService.buscar(fornecedorId);
        }

        if (!Boolean.TRUE.equals(fornecedor.getAtivo())) {
            throw new NegocioException("Fornecedor inativo.");
        }

        Produto produto = new Produto();
        produto.setFornecedor(fornecedor);
        produto.setPedido(pedido);
        produto.setStatus(StatusProduto.RASCUNHO);
        validarRegras(request, fornecedor, null);
        aplicar(produto, request, fornecedor);

        Produto salvo = produtoRepository.save(produto);
        registrar(salvo, logado, "CRIACAO", null, null, salvo.getCodigo());
        return salvo;
    }

    @Transactional
    public Produto atualizar(Long id, ProdutoRequest request, UsuarioAutenticado logado) {
        Produto produto = buscar(id, logado);
        if (!logado.isAdmin() && !produto.isEditavelPeloFornecedor()) {
            throw new NegocioException("Produto ja enviado/aprovado nao pode ser alterado. "
                    + "Solicite a reabertura ao time de cadastro.");
        }
        Fornecedor fornecedor = produto.getFornecedor();
        validarRegras(request, fornecedor, produto.getId());

        List<ProdutoHistorico> alteracoes = diferencas(produto, request, logado);
        aplicar(produto, request, fornecedor);
        if (produto.getStatus() == StatusProduto.REPROVADO) {
            produto.setStatus(StatusProduto.RASCUNHO);
            produto.setParecer(null);
        }
        Produto salvo = produtoRepository.save(produto);
        if (!alteracoes.isEmpty()) {
            historicoRepository.saveAll(alteracoes);
        }
        return salvo;
    }

    @Transactional
    public Produto enviar(Long id, UsuarioAutenticado logado) {
        Produto produto = buscar(id, logado);
        if (produto.getStatus() == StatusProduto.ENVIADO) {
            throw new NegocioException("Produto ja enviado e aguardando analise.");
        }
        if (produto.getStatus() == StatusProduto.APROVADO) {
            throw new NegocioException("Produto ja aprovado.");
        }
        produto.setStatus(StatusProduto.ENVIADO);
        produto.setDataEnvio(OffsetDateTime.now());
        Produto salvo = produtoRepository.save(produto);
        registrar(salvo, logado, "ENVIO", null, null, "ENVIADO");
        return salvo;
    }

    @Transactional
    public Produto analisar(Long id, boolean aprovado, String parecer, UsuarioAutenticado logado) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto nao encontrado."));
        if (produto.getStatus() != StatusProduto.ENVIADO) {
            throw new NegocioException("Somente produtos com status ENVIADO podem ser analisados.");
        }
        if (!aprovado && (parecer == null || parecer.isBlank())) {
            throw new NegocioException("Informe o motivo da reprovacao.");
        }
        produto.setStatus(aprovado ? StatusProduto.APROVADO : StatusProduto.REPROVADO);
        produto.setParecer(parecer);
        produto.setDataAnalise(OffsetDateTime.now());
        produto.setUsuarioAnalise(usuarioRepository.getReferenceById(logado.getId()));
        Produto salvo = produtoRepository.save(produto);
        registrar(salvo, logado, aprovado ? "APROVACAO" : "REPROVACAO", null, null,
                aprovado ? "APROVADO" : "REPROVADO: " + parecer);
        return salvo;
    }

    /** Desfaz a ressalva marcada num item, devolvendo-o para a analise do pedido. */
    @Transactional
    public Produto retirarRessalva(Long id, UsuarioAutenticado logado) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto nao encontrado."));
        if (produto.getStatus() != StatusProduto.REPROVADO) {
            throw new NegocioException("Este item nao esta com ressalva.");
        }
        produto.setStatus(StatusProduto.ENVIADO);
        produto.setParecer(null);
        produto.setDataAnalise(null);
        produto.setUsuarioAnalise(null);
        Produto salvo = produtoRepository.save(produto);
        registrar(salvo, logado, "RESSALVA_RETIRADA", null, null, "ENVIADO");
        return salvo;
    }

    @Transactional
    public Produto reabrir(Long id, UsuarioAutenticado logado) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto nao encontrado."));
        if (produto.getStatus() == StatusProduto.RASCUNHO) {
            throw new NegocioException("Produto ja esta em rascunho.");
        }
        produto.setStatus(StatusProduto.RASCUNHO);
        produto.setDataAnalise(null);
        produto.setUsuarioAnalise(null);
        Produto salvo = produtoRepository.save(produto);
        registrar(salvo, logado, "REABERTURA", null, null, "RASCUNHO");
        return salvo;
    }

    @Transactional
    public void excluir(Long id, UsuarioAutenticado logado) {
        Produto produto = buscar(id, logado);
        if (!logado.isAdmin() && produto.getStatus() != StatusProduto.RASCUNHO) {
            throw new NegocioException("Somente produtos em rascunho podem ser excluidos.");
        }
        fotoRepository.deleteByProdutoId(id);
        historicoRepository.deleteAll(historicoRepository.listarPorProduto(id));
        produtoRepository.delete(produto);
    }

    // ------------------------------------------------------------------ apoio

    private void validarAcesso(Produto produto, UsuarioAutenticado logado) {
        if (!logado.isAdmin() && !Objects.equals(produto.getFornecedor().getId(), logado.getFornecedorId())) {
            throw new RecursoNaoEncontradoException("Produto nao encontrado.");
        }
    }

    private void validarRegras(ProdutoRequest r, Fornecedor fornecedor, Long produtoId) {
        Long fornecedorId = fornecedor.getId();
        boolean duplicado = (produtoId == null)
                ? produtoRepository.existsByFornecedorIdAndCodigoIgnoreCase(fornecedorId, r.codigo().trim())
                : produtoRepository.existsByFornecedorIdAndCodigoIgnoreCaseAndIdNot(fornecedorId, r.codigo().trim(), produtoId);
        if (duplicado) {
            throw new NegocioException("Ja existe um produto com o codigo " + r.codigo() + " para este fornecedor.");
        }

        String ean = r.codBarraProduto().trim();
        boolean eanDuplicado = (produtoId == null)
                ? produtoRepository.existsByCodBarraProduto(ean)
                : produtoRepository.existsByCodBarraProdutoAndIdNot(ean, produtoId);
        if (eanDuplicado) {
            throw new NegocioException("O codigo de barras " + ean + " ja esta cadastrado em outro produto.");
        }

        if (r.pesoBruto().compareTo(r.pesoLiquido()) < 0) {
            throw new NegocioException(fornecedor.getTipo().isPiso()
                    ? "O peso/m2 bruto nao pode ser menor que o peso/m2 liquido."
                    : "O peso bruto nao pode ser menor que o peso liquido.");
        }

        if (fornecedor.getTipo().isPiso()) {
            // piso e revestimento: tudo em m2, sem cubagem da caixa
            if (r.m2Caixa() == null) {
                throw new NegocioException("Informe o m2 por caixa.");
            }
            if (r.m2Pallet() != null && r.m2Pallet().compareTo(r.m2Caixa()) < 0) {
                throw new NegocioException("O m2 por pallet nao pode ser menor que o m2 por caixa.");
            }
        } else {
            if (r.qtdEmbalagem() == null) {
                throw new NegocioException("Informe a quantidade por embalagem / caixa master.");
            }
            if (r.metroCaixa() != null && r.metroPallet() != null
                    && r.metroPallet().compareTo(r.metroCaixa()) < 0) {
                throw new NegocioException("O metro quadrado do pallet nao pode ser menor que o metro quadrado da caixa.");
            }
        }
        if (r.codBarraCaixa() != null && !r.codBarraCaixa().isBlank()
                && r.codBarraCaixa().trim().equals(ean)) {
            throw new NegocioException("O codigo de barras da caixa master (DUN) deve ser diferente do EAN do produto.");
        }
    }

    private void aplicar(Produto p, ProdutoRequest r, Fornecedor fornecedor) {
        p.setCodigo(r.codigo().trim());
        p.setDescricao(r.descricao().trim());
        p.setCodBarraCaixa(vazioParaNulo(r.codBarraCaixa()));
        p.setCodBarraProduto(r.codBarraProduto().trim());
        p.setNcm(r.ncm().trim());
        p.setPesoLiquido(r.pesoLiquido());
        p.setPesoBruto(r.pesoBruto());
        if (fornecedor.getTipo().isPiso()) {
            p.setM2Caixa(r.m2Caixa());
            p.setM2Pallet(r.m2Pallet());
            p.setQtdEmbalagem(null);
            p.setMetroCaixa(null);
            p.setMetroPallet(null);
        } else {
            p.setQtdEmbalagem(r.qtdEmbalagem());
            p.setMetroCaixa(r.metroCaixa());
            p.setMetroPallet(r.metroPallet());
            p.setM2Caixa(null);
            p.setM2Pallet(null);
        }
        p.setObservacao(vazioParaNulo(r.observacao()));
    }

    private List<ProdutoHistorico> diferencas(Produto atual, ProdutoRequest novo, UsuarioAutenticado logado) {
        Usuario usuario = usuarioRepository.getReferenceById(logado.getId());
        List<ProdutoHistorico> lista = new ArrayList<>();
        comparar(lista, atual, usuario, "codigo", atual.getCodigo(), novo.codigo());
        comparar(lista, atual, usuario, "descricao", atual.getDescricao(), novo.descricao());
        comparar(lista, atual, usuario, "codBarraCaixa", atual.getCodBarraCaixa(), novo.codBarraCaixa());
        comparar(lista, atual, usuario, "codBarraProduto", atual.getCodBarraProduto(), novo.codBarraProduto());
        comparar(lista, atual, usuario, "ncm", atual.getNcm(), novo.ncm());
        compararNumero(lista, atual, usuario, "pesoLiquido", atual.getPesoLiquido(), novo.pesoLiquido());
        compararNumero(lista, atual, usuario, "pesoBruto", atual.getPesoBruto(), novo.pesoBruto());
        compararNumero(lista, atual, usuario, "metroCaixa", atual.getMetroCaixa(), novo.metroCaixa());
        compararNumero(lista, atual, usuario, "metroPallet", atual.getMetroPallet(), novo.metroPallet());
        comparar(lista, atual, usuario, "qtdEmbalagem",
                texto(atual.getQtdEmbalagem()), texto(novo.qtdEmbalagem()));
        compararNumero(lista, atual, usuario, "m2Caixa", atual.getM2Caixa(), novo.m2Caixa());
        compararNumero(lista, atual, usuario, "m2Pallet", atual.getM2Pallet(), novo.m2Pallet());
        comparar(lista, atual, usuario, "observacao", atual.getObservacao(), novo.observacao());
        return lista;
    }

    private void comparar(List<ProdutoHistorico> lista, Produto p, Usuario u,
                          String campo, String anterior, String novo) {
        String a = vazioParaNulo(anterior);
        String n = vazioParaNulo(novo);
        if (!Objects.equals(a, n)) {
            lista.add(ProdutoHistorico.de(p, u, "ALTERACAO", campo, a, n));
        }
    }

    private void compararNumero(List<ProdutoHistorico> lista, Produto p, Usuario u,
                                String campo, BigDecimal anterior, BigDecimal novo) {
        boolean iguais = (anterior == null && novo == null)
                || (anterior != null && novo != null && anterior.compareTo(novo) == 0);
        if (!iguais) {
            lista.add(ProdutoHistorico.de(p, u, "ALTERACAO", campo, texto(anterior), texto(novo)));
        }
    }

    private String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }

    private String vazioParaNulo(String valor) {
        if (valor == null) return null;
        String t = valor.trim();
        return t.isEmpty() ? null : t;
    }

    private void registrar(Produto produto, UsuarioAutenticado logado, String acao,
                           String campo, String anterior, String novo) {
        Usuario usuario = usuarioRepository.getReferenceById(logado.getId());
        historicoRepository.save(ProdutoHistorico.de(produto, usuario, acao, campo, anterior, novo));
    }
}
