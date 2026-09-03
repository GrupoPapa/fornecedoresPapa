package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.*;
import br.com.grupopapa.cfp.dto.PedidoRequest;
import br.com.grupopapa.cfp.exception.NegocioException;
import br.com.grupopapa.cfp.exception.RecursoNaoEncontradoException;
import br.com.grupopapa.cfp.repository.*;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoFotoRepository fotoRepository;
    private final ProdutoHistoricoRepository historicoRepository;
    private final UsuarioRepository usuarioRepository;
    private final FornecedorService fornecedorService;
    private final ProdutoErpRepository produtoErpRepository;
    private final boolean exigirComplementoErp;

    public PedidoService(PedidoRepository pedidoRepository,
                         ProdutoRepository produtoRepository,
                         ProdutoFotoRepository fotoRepository,
                         ProdutoHistoricoRepository historicoRepository,
                         UsuarioRepository usuarioRepository,
                         FornecedorService fornecedorService,
                         ProdutoErpRepository produtoErpRepository,
                         @org.springframework.beans.factory.annotation.Value(
                                 "${app.erp.exigir-complemento:true}") boolean exigirComplementoErp) {
        this.produtoErpRepository = produtoErpRepository;
        this.exigirComplementoErp = exigirComplementoErp;
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.fotoRepository = fotoRepository;
        this.historicoRepository = historicoRepository;
        this.usuarioRepository = usuarioRepository;
        this.fornecedorService = fornecedorService;
    }

    // ------------------------------------------------------------------ consulta

    @Transactional(readOnly = true)
    public Page<Pedido> listar(Long fornecedorId, StatusPedido status, String termo, Pageable pageable) {
        return pedidoRepository.pesquisar(fornecedorId, status,
                (termo == null || termo.isBlank()) ? null : termo.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public Pedido buscar(Long id, UsuarioAutenticado logado) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido nao encontrado."));
        if (!logado.isAdmin() && !Objects.equals(pedido.getFornecedor().getId(), logado.getFornecedorId())) {
            throw new RecursoNaoEncontradoException("Pedido nao encontrado.");
        }
        return pedido;
    }

    /** Mostra ate 4 campos pendentes para a mensagem nao ficar gigante. */
    private String resumir(List<String> campos) {
        if (campos.size() <= 4) return String.join(", ", campos);
        return String.join(", ", campos.subList(0, 4)) + " e mais " + (campos.size() - 4);
    }

    @Transactional(readOnly = true)
    public List<Produto> itens(Long pedidoId) {
        return produtoRepository.findByPedidoIdOrderByCodigoAsc(pedidoId);
    }

    @Transactional(readOnly = true)
    public Pedido pedidoAberto(Long fornecedorId) {
        return pedidoRepository.findByFornecedorIdAndStatus(fornecedorId, StatusPedido.ABERTO).orElse(null);
    }

    // ------------------------------------------------------------------ escrita

    /** Devolve o pedido aberto do fornecedor ou cria um novo. So existe um aberto por vez. */
    @Transactional
    public Pedido abrirOuObter(UsuarioAutenticado logado, Long fornecedorIdInformado) {
        Long fornecedorId = logado.isAdmin() ? fornecedorIdInformado : logado.getFornecedorId();
        if (fornecedorId == null) {
            throw new NegocioException("Informe o fornecedor do pedido.");
        }
        Pedido aberto = pedidoAberto(fornecedorId);
        if (aberto != null) {
            return aberto;
        }

        Fornecedor fornecedor = fornecedorService.buscar(fornecedorId);
        if (!Boolean.TRUE.equals(fornecedor.getAtivo())) {
            throw new NegocioException("Fornecedor inativo.");
        }

        // a numeracao por ano pode colidir com outro usuario criando ao mesmo tempo
        DataIntegrityViolationException ultimoErro = null;
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            try {
                return pedidoRepository.saveAndFlush(novoPedido(fornecedor));
            } catch (DataIntegrityViolationException e) {
                ultimoErro = e;
                Pedido concorrente = pedidoAberto(fornecedorId);
                if (concorrente != null) {
                    return concorrente;
                }
            }
        }
        throw new NegocioException("Nao foi possivel abrir o pedido agora. Tente novamente. "
                + (ultimoErro == null ? "" : ultimoErro.getMostSpecificCause().getMessage()));
    }

    private Pedido novoPedido(Fornecedor fornecedor) {
        int ano = LocalDate.now().getYear();
        int sequencia = pedidoRepository.maiorSequencia(ano) + 1;

        Pedido pedido = new Pedido();
        pedido.setFornecedor(fornecedor);
        pedido.setAno(ano);
        pedido.setSequencia(sequencia);
        pedido.setNumero(String.format("%d/%05d", ano, sequencia));
        pedido.setStatus(StatusPedido.ABERTO);
        return pedido;
    }

    @Transactional
    public Pedido atualizar(Long id, PedidoRequest request, UsuarioAutenticado logado) {
        Pedido pedido = buscar(id, logado);
        if (!logado.isAdmin() && !pedido.isEditavelPeloFornecedor()) {
            throw new NegocioException("Pedido ja enviado nao pode ser alterado.");
        }
        pedido.setObservacao(request.observacao() == null || request.observacao().isBlank()
                ? null : request.observacao().trim());
        return pedidoRepository.save(pedido);
    }

    /** Envia (ou reenvia) o pedido para analise. */
    @Transactional
    public Pedido enviar(Long id, UsuarioAutenticado logado) {
        Pedido pedido = buscar(id, logado);
        if (pedido.getStatus() == StatusPedido.ENVIADO) {
            throw new NegocioException("Pedido " + pedido.getNumero() + " ja esta em analise.");
        }
        if (pedido.getStatus() == StatusPedido.APROVADO) {
            throw new NegocioException("Pedido " + pedido.getNumero() + " ja foi aprovado.");
        }

        List<Produto> itens = itens(id);
        if (itens.isEmpty()) {
            throw new NegocioException("Adicione ao menos um produto antes de enviar o pedido.");
        }

        Usuario usuario = usuarioRepository.getReferenceById(logado.getId());
        OffsetDateTime agora = OffsetDateTime.now();

        for (Produto item : itens) {
            if (item.getStatus() == StatusProduto.APROVADO) {
                continue; // item ja aprovado numa analise anterior segue como esta
            }
            item.setStatus(StatusProduto.ENVIADO);
            item.setDataEnvio(agora);
            historicoRepository.save(ProdutoHistorico.de(item, usuario, "ENVIO", null, null,
                    "Enviado no pedido " + pedido.getNumero()));
        }
        produtoRepository.saveAll(itens);

        pedido.setStatus(StatusPedido.ENVIADO);
        pedido.setDataEnvio(agora);
        pedido.setParecer(null);
        return pedidoRepository.save(pedido);
    }

    /**
     * Analise do pedido inteiro.
     * Aprovar exige que nenhum item esteja com ressalva; reprovar devolve para correcao
     * e mantem aprovados os itens que passaram.
     */
    @Transactional
    public Pedido analisar(Long id, boolean aprovado, String parecer, UsuarioAutenticado logado) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido nao encontrado."));
        if (pedido.getStatus() != StatusPedido.ENVIADO) {
            throw new NegocioException("Somente pedidos com status ENVIADO podem ser analisados.");
        }

        List<Produto> itens = itens(id);
        if (itens.isEmpty()) {
            throw new NegocioException("Pedido sem itens.");
        }

        List<Produto> comRessalva = itens.stream()
                .filter(i -> i.getStatus() == StatusProduto.REPROVADO).toList();

        if (aprovado && exigirComplementoErp) {
            var complementos = produtoErpRepository.listarPorPedido(id).stream()
                    .collect(java.util.stream.Collectors.toMap(
                            br.com.grupopapa.cfp.domain.ProdutoErp::getProdutoId, e -> e));
            List<String> pendentes = new java.util.ArrayList<>();
            for (Produto item : itens) {
                var erp = complementos.get(item.getId());
                List<String> faltam = (erp == null)
                        ? List.of("todos os campos")
                        : erp.camposFaltantes();
                if (!faltam.isEmpty()) {
                    pendentes.add(item.getCodigo() + " (falta: " + resumir(faltam) + ")");
                }
            }
            if (!pendentes.isEmpty()) {
                throw new NegocioException(
                        "Todos os campos do WinThor precisam estar preenchidos para aprovar o pedido. "
                        + "Pendente(s): " + String.join("; ", pendentes) + ".");
            }
        }

        if (aprovado && !comRessalva.isEmpty()) {
            throw new NegocioException("Existem " + comRessalva.size()
                    + " item(ns) com ressalva. Retire a ressalva ou reprove o pedido.");
        }
        if (!aprovado && comRessalva.isEmpty() && (parecer == null || parecer.isBlank())) {
            throw new NegocioException("Marque a ressalva nos itens com problema ou informe o motivo da reprovacao.");
        }

        Usuario usuario = usuarioRepository.getReferenceById(logado.getId());
        OffsetDateTime agora = OffsetDateTime.now();

        for (Produto item : itens) {
            if (item.getStatus() == StatusProduto.REPROVADO) {
                continue; // mantem a ressalva para o fornecedor corrigir
            }
            item.setStatus(StatusProduto.APROVADO);
            item.setDataAnalise(agora);
            item.setUsuarioAnalise(usuario);
            historicoRepository.save(ProdutoHistorico.de(item, usuario, "APROVACAO", null, null,
                    "Aprovado na analise do pedido " + pedido.getNumero()));
        }
        produtoRepository.saveAll(itens);

        pedido.setStatus(aprovado ? StatusPedido.APROVADO : StatusPedido.REPROVADO);
        pedido.setParecer(parecer == null || parecer.isBlank() ? null : parecer.trim());
        pedido.setDataAnalise(agora);
        pedido.setUsuarioAnalise(usuario);
        return pedidoRepository.save(pedido);
    }

    /** Volta um pedido aprovado para analise. */
    @Transactional
    public Pedido reabrir(Long id, UsuarioAutenticado logado) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido nao encontrado."));
        if (pedido.getStatus() != StatusPedido.APROVADO) {
            throw new NegocioException("Somente pedidos aprovados podem ser reabertos.");
        }
        List<Produto> itens = itens(id);
        for (Produto item : itens) {
            item.setStatus(StatusProduto.ENVIADO);
            item.setDataAnalise(null);
            item.setUsuarioAnalise(null);
        }
        produtoRepository.saveAll(itens);

        pedido.setStatus(StatusPedido.ENVIADO);
        pedido.setDataAnalise(null);
        pedido.setUsuarioAnalise(null);
        return pedidoRepository.save(pedido);
    }

    /** Exclui um pedido ainda aberto, junto com os itens que estiverem nele. */
    @Transactional
    public void excluir(Long id, UsuarioAutenticado logado) {
        Pedido pedido = buscar(id, logado);
        if (pedido.getStatus() != StatusPedido.ABERTO) {
            throw new NegocioException("Somente pedidos em aberto podem ser cancelados.");
        }
        for (Produto item : itens(id)) {
            fotoRepository.deleteByProdutoId(item.getId());
            historicoRepository.deleteAll(historicoRepository.listarPorProduto(item.getId()));
            produtoRepository.delete(item);
        }
        pedidoRepository.delete(pedido);
    }
}
