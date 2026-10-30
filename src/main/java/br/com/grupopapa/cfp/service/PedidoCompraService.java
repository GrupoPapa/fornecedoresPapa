package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.*;
import br.com.grupopapa.cfp.dto.AnaliseCompraRequest;
import br.com.grupopapa.cfp.dto.CatalogoCompraResponse;
import br.com.grupopapa.cfp.dto.ComparativoPrecoResponse;
import br.com.grupopapa.cfp.dto.PedidoCompraRequest;
import br.com.grupopapa.cfp.exception.NegocioException;
import br.com.grupopapa.cfp.exception.RecursoNaoEncontradoException;
import br.com.grupopapa.cfp.repository.*;
import br.com.grupopapa.cfp.repository.ErpCompraRepository.ProdutoWinthor;
import br.com.grupopapa.cfp.repository.ErpCompraRepository.UltimaCompra;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Pedido de compra digitado pelo fornecedor, seguindo o FLUXO PEDIDOS:
 * digitacao -> verificacao de divergencias no cadastro -> (reanalise do cadastro pelo
 * fiscal) -> analise fiscal com comparativo dos ultimos precos -> aprovado (gera a
 * sugestao para a rotina 220) ou reprovado (fornecedor reavalia e reenvia).
 */
@Service
public class PedidoCompraService {

    private static final Logger log = LoggerFactory.getLogger(PedidoCompraService.class);

    private final PedidoCompraRepository repository;
    private final PedidoCompraHistoricoRepository historicoRepository;
    private final FornecedorService fornecedorService;
    private final UsuarioRepository usuarioRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoNovoRepository produtoNovoRepository;
    private final ProdutoErpRepository produtoErpRepository;
    private final ErpCompraRepository erp;
    private final int qtdHistoricoPrecos;
    private final BigDecimal alertaPercentual;

    public PedidoCompraService(PedidoCompraRepository repository,
                               PedidoCompraHistoricoRepository historicoRepository,
                               FornecedorService fornecedorService,
                               UsuarioRepository usuarioRepository,
                               ProdutoRepository produtoRepository,
                               ProdutoNovoRepository produtoNovoRepository,
                               ProdutoErpRepository produtoErpRepository,
                               ErpCompraRepository erp,
                               @Value("${app.erp.compra.qtd-historico-precos:5}") int qtdHistoricoPrecos,
                               @Value("${app.erp.compra.alerta-percentual:10}") BigDecimal alertaPercentual) {
        this.repository = repository;
        this.historicoRepository = historicoRepository;
        this.fornecedorService = fornecedorService;
        this.usuarioRepository = usuarioRepository;
        this.produtoRepository = produtoRepository;
        this.produtoNovoRepository = produtoNovoRepository;
        this.produtoErpRepository = produtoErpRepository;
        this.erp = erp;
        this.qtdHistoricoPrecos = Math.max(1, Math.min(qtdHistoricoPrecos, 20));
        this.alertaPercentual = alertaPercentual == null ? BigDecimal.TEN : alertaPercentual.abs();
    }

    // ================================================================ consulta

    @Transactional(readOnly = true)
    public Page<PedidoCompra> listar(Long fornecedorId, StatusPedidoCompra status, String termo, Pageable pageable) {
        return repository.pesquisar(fornecedorId, status,
                (termo == null || termo.isBlank()) ? null : termo.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public PedidoCompra buscar(Long id, UsuarioAutenticado logado) {
        PedidoCompra pedido = repository.buscarComItens(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido de compra nao encontrado."));
        if (!logado.isAdmin() && !Objects.equals(pedido.getFornecedor().getId(), logado.getFornecedorId())) {
            throw new RecursoNaoEncontradoException("Pedido de compra nao encontrado.");
        }
        return pedido;
    }

    @Transactional(readOnly = true)
    public List<PedidoCompraHistorico> historico(Long id, UsuarioAutenticado logado) {
        buscar(id, logado);
        return historicoRepository.listar(id);
    }

    @Transactional(readOnly = true)
    public List<PedidoCompra> listarParaExportacao(Long fornecedorId, StatusPedidoCompra status) {
        return repository.listarParaExportacao(fornecedorId, status, null);
    }

    public List<ErpCompraRepository.Filial> filiais() {
        return erp.filiais();
    }

    /** Produtos do WinThor do fornecedor + produtos novos que ele cadastrou no portal. */
    @Transactional(readOnly = true)
    public CatalogoCompraResponse catalogo(UsuarioAutenticado logado, Long fornecedorIdInformado) {
        Fornecedor fornecedor = fornecedorDoEscopo(logado, fornecedorIdInformado);
        List<CatalogoCompraResponse.Produto> produtos = new ArrayList<>();
        String aviso = null;

        Set<Long> codigosWinthor = new HashSet<>();
        if (fornecedor.getCodFornecedorErp() == null) {
            aviso = "Fornecedor sem codigo no WinThor: so os produtos novos do portal aparecem. "
                    + "Peca a equipe para importar o cadastro do fornecedor.";
        } else {
            try {
                for (ProdutoWinthor p : erp.produtosDoFornecedor(fornecedor.getCodFornecedorErp())) {
                    codigosWinthor.add(p.codProd());
                    produtos.add(new CatalogoCompraResponse.Produto("WINTHOR", p.codProd(), null, null,
                            p.codFab(), p.descricao(), p.embalagem(), p.unidade(), p.qtUnitCx(), p.codAuxiliar()));
                }
            } catch (DataAccessException e) {
                log.warn("Falha ao ler os produtos do fornecedor {} no WinThor: {}",
                        fornecedor.getCodFornecedorErp(), e.getMostSpecificCause().getMessage());
                aviso = "Nao foi possivel carregar os produtos do WinThor agora. Tente novamente em instantes.";
            }
        }

        for (Produto p : produtoNovoRepository.semCodigoWinthor(fornecedor.getId())) {
            produtos.add(new CatalogoCompraResponse.Produto("PORTAL", null, p.getId(), p.getStatus(),
                    p.getCodigo(), p.getDescricao(), null, null, null, p.getCodBarraProduto()));
        }
        return new CatalogoCompraResponse(produtos, aviso, fornecedor.getCodFilialCompra());
    }

    // ================================================================ fornecedor

    @Transactional
    public PedidoCompra criar(PedidoCompraRequest request, UsuarioAutenticado logado, Long fornecedorIdInformado) {
        Fornecedor fornecedor = fornecedorDoEscopo(logado, fornecedorIdInformado);
        if (!Boolean.TRUE.equals(fornecedor.getAtivo())) {
            throw new NegocioException("Fornecedor inativo.");
        }
        Usuario usuario = usuarioRepository.getReferenceById(logado.getId());

        DataIntegrityViolationException ultimoErro = null;
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            try {
                PedidoCompra pedido = new PedidoCompra();
                int ano = LocalDate.now().getYear();
                int sequencia = repository.maiorSequencia(ano) + 1;
                pedido.setAno(ano);
                pedido.setSequencia(sequencia);
                pedido.setNumero(String.format("%d/%05d", ano, sequencia));
                pedido.setFornecedor(fornecedor);
                pedido.setUsuarioCriacao(usuario);
                aplicar(pedido, request);
                pedido = repository.saveAndFlush(pedido);
                registrar(pedido.getId(), usuario, "CRIACAO",
                        "Pedido criado com " + pedido.getItens().size() + " item(ns).");
                return pedido;
            } catch (DataIntegrityViolationException e) {
                ultimoErro = e; // numero colidiu com outro pedido criado ao mesmo tempo
            }
        }
        throw new NegocioException("Nao foi possivel criar o pedido agora. Tente novamente. "
                + (ultimoErro == null ? "" : ultimoErro.getMostSpecificCause().getMessage()));
    }

    @Transactional
    public PedidoCompra atualizar(Long id, PedidoCompraRequest request, UsuarioAutenticado logado) {
        PedidoCompra pedido = buscar(id, logado);
        if (!pedido.isEditavelPeloFornecedor()) {
            throw new NegocioException("O pedido " + pedido.getNumero()
                    + " esta " + rotulo(pedido.getStatus()) + " e nao pode mais ser alterado.");
        }
        aplicar(pedido, request);
        return repository.save(pedido);
    }

    /** Envia (ou reenvia, apos reprovacao) e ja verifica as divergencias de cadastro. */
    @Transactional
    public PedidoCompra enviar(Long id, UsuarioAutenticado logado) {
        PedidoCompra pedido = buscar(id, logado);
        if (!pedido.isEditavelPeloFornecedor()) {
            throw new NegocioException("O pedido " + pedido.getNumero() + " ja esta " + rotulo(pedido.getStatus()) + ".");
        }
        if (pedido.getItens().isEmpty()) {
            throw new NegocioException("Adicione ao menos um produto antes de enviar o pedido.");
        }
        if (pedido.getCodFilial() == null) {
            throw new NegocioException("Informe a filial de entrega antes de enviar.");
        }
        if (pedido.getDataPrevisaoEntrega() == null) {
            throw new NegocioException("Informe a previsao de entrega antes de enviar.");
        }
        if (pedido.getDataPrevisaoEntrega().isBefore(LocalDate.now())) {
            throw new NegocioException("A previsao de entrega nao pode ser uma data passada.");
        }

        boolean reenvio = pedido.getStatus() == StatusPedidoCompra.REVISAO;
        Usuario usuario = usuarioRepository.getReferenceById(logado.getId());
        List<String> divergencias = verificarDivergencias(pedido);

        pedido.setDataEnvio(OffsetDateTime.now());
        pedido.setStatus(divergencias.isEmpty() ? StatusPedidoCompra.EM_ANALISE : StatusPedidoCompra.DIVERGENCIA);
        repository.save(pedido);

        registrar(pedido.getId(), usuario, reenvio ? "REENVIO" : "ENVIO",
                (reenvio ? "Reenviado apos revisao. " : "Enviado para o fiscal. ")
                + pedido.getItens().size() + " item(ns), total R$ " + pedido.getValorTotal() + ".");
        if (!divergencias.isEmpty()) {
            registrar(pedido.getId(), null, "DIVERGENCIA",
                    "Divergencia no cadastro: " + String.join("; ", divergencias));
        }
        return pedido;
    }

    @Transactional
    public PedidoCompra cancelar(Long id, String motivo, UsuarioAutenticado logado) {
        PedidoCompra pedido = buscar(id, logado);
        boolean podeCancelar = logado.isAdmin()
                ? pedido.getStatus() != StatusPedidoCompra.APROVADO && pedido.getStatus() != StatusPedidoCompra.CANCELADO
                  && pedido.getStatus() != StatusPedidoCompra.REPROVADO
                : pedido.isEditavelPeloFornecedor();
        if (!podeCancelar) {
            throw new NegocioException("O pedido " + pedido.getNumero() + " nao pode ser cancelado ("
                    + rotulo(pedido.getStatus()) + ").");
        }
        pedido.setStatus(StatusPedidoCompra.CANCELADO);
        repository.save(pedido);
        registrar(id, usuarioRepository.getReferenceById(logado.getId()), "CANCELAMENTO",
                motivo == null || motivo.isBlank() ? "Pedido cancelado." : "Pedido cancelado: " + motivo.trim());
        return pedido;
    }

    // ================================================================ fiscal

    /**
     * "Fiscal reanalisa cadastro": confere de novo os itens divergentes. Produto novo so
     * libera depois de gravado no WinThor; as demais divergencias podem ser liberadas
     * pelo fiscal com forcar = true e um parecer.
     */
    @Transactional
    public PedidoCompra resolverDivergencia(Long id, AnaliseCompraRequest request, UsuarioAutenticado logado) {
        PedidoCompra pedido = buscar(id, logado);
        if (pedido.getStatus() != StatusPedidoCompra.DIVERGENCIA) {
            throw new NegocioException("O pedido nao esta com divergencia de cadastro.");
        }
        Usuario usuario = usuarioRepository.getReferenceById(logado.getId());
        List<String> restantes = verificarDivergencias(pedido);

        if (!restantes.isEmpty()) {
            List<String> semCodigo = pedido.getItens().stream()
                    .filter(PedidoCompraItem::isProdutoNovo)
                    .map(PedidoCompraItem::getDescricao).toList();
            if (!semCodigo.isEmpty()) {
                repository.save(pedido);
                throw new NegocioException("Ainda ha produto novo sem codigo no WinThor: "
                        + String.join(", ", semCodigo) + ". Aprove o cadastro e use \"Gravar no WinThor\" "
                        + "na aba Cadastro de produtos; depois volte aqui.");
            }
            boolean forcar = request != null && Boolean.TRUE.equals(request.forcar());
            String parecer = request == null ? null : request.parecer();
            if (!forcar) {
                repository.save(pedido);
                throw new NegocioException("Divergencias pendentes: " + String.join("; ", restantes)
                        + ". Corrija o cadastro ou libere informando o motivo.");
            }
            if (parecer == null || parecer.isBlank()) {
                throw new NegocioException("Informe o motivo para liberar o pedido com divergencia.");
            }
            pedido.getItens().forEach(i -> i.setDivergencia(null));
            registrar(id, usuario, "DIVERGENCIA_LIBERADA",
                    "Liberado pelo fiscal: " + parecer.trim() + " (" + String.join("; ", restantes) + ")");
        } else {
            registrar(id, usuario, "CADASTRO_REANALISADO", "Cadastro conferido; pedido segue para analise de precos.");
        }
        pedido.setStatus(StatusPedidoCompra.EM_ANALISE);
        return repository.save(pedido);
    }

    /** Revisar: devolve ao fornecedor com o motivo; ele corrige e reenvia. */
    @Transactional
    public PedidoCompra revisar(Long id, String parecer, UsuarioAutenticado logado) {
        return encerrarAnalise(id, parecer, logado, StatusPedidoCompra.REVISAO, "REVISAO",
                "Informe o que o fornecedor precisa corrigir.", "Devolvido para revisao: ");
    }

    /** Reprovar: encerra o pedido. O fornecedor ve o motivo, mas nao pode mais altera-lo. */
    @Transactional
    public PedidoCompra reprovar(Long id, String parecer, UsuarioAutenticado logado) {
        return encerrarAnalise(id, parecer, logado, StatusPedidoCompra.REPROVADO, "REPROVACAO",
                "Informe o motivo da reprovacao.", "Reprovado: ");
    }

    private PedidoCompra encerrarAnalise(Long id, String parecer, UsuarioAutenticado logado,
                                         StatusPedidoCompra destino, String evento,
                                         String exigirMotivo, String prefixo) {
        PedidoCompra pedido = buscar(id, logado);
        if (!pedido.getStatus().isEmAnalise()) {
            throw new NegocioException("Somente pedidos em analise podem ser revisados ou reprovados.");
        }
        if (parecer == null || parecer.isBlank()) {
            throw new NegocioException(exigirMotivo);
        }
        Usuario usuario = usuarioRepository.getReferenceById(logado.getId());
        pedido.setStatus(destino);
        pedido.setParecer(cortar(parecer.trim(), 500));
        pedido.setDataAnalise(OffsetDateTime.now());
        pedido.setUsuarioAnalise(usuario);
        repository.save(pedido);
        registrar(id, usuario, evento, prefixo + parecer.trim());
        return pedido;
    }

    /** Aprova. A geracao da sugestao no WinThor e feita em seguida pelo SugestaoCompraService. */
    @Transactional
    public PedidoCompra aprovar(Long id, AnaliseCompraRequest request, UsuarioAutenticado logado) {
        PedidoCompra pedido = buscar(id, logado);
        if (pedido.getStatus() == StatusPedidoCompra.DIVERGENCIA) {
            throw new NegocioException("Resolva a divergencia de cadastro antes de aprovar.");
        }
        if (pedido.getStatus() != StatusPedidoCompra.EM_ANALISE) {
            throw new NegocioException("Somente pedidos em analise podem ser aprovados.");
        }
        if (request != null && request.codFilial() != null && !request.codFilial().isBlank()) {
            pedido.setCodFilial(validarFilial(request.codFilial().trim()));
        }
        if (pedido.getCodFilial() == null) {
            throw new NegocioException("Informe a filial do pedido.");
        }
        if (pedido.getFornecedor().getCodFornecedorErp() == null) {
            throw new NegocioException("O fornecedor nao tem codigo do WinThor. Importe-o na tela de fornecedores.");
        }
        List<String> semCodigo = pedido.getItens().stream()
                .filter(PedidoCompraItem::isProdutoNovo).map(PedidoCompraItem::getDescricao).toList();
        if (!semCodigo.isEmpty()) {
            throw new NegocioException("Itens sem codigo no WinThor: " + String.join(", ", semCodigo) + ".");
        }

        try {
            Set<Long> semTabela = erp.semTabelaPreco(pedido.getItens().stream()
                    .map(PedidoCompraItem::getCodProdErp).toList(), pedido.getCodFilial());
            if (!semTabela.isEmpty()) {
                throw new NegocioException("A rotina 220 vai recusar estes produtos por falta de tabela de "
                        + "preco/tributacao na filial " + pedido.getCodFilial() + ": "
                        + semTabela.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "))
                        + ". Abra cada um na rotina 203, edite e salve, e aprove de novo.");
            }
        } catch (DataAccessException e) {
            log.warn("Nao foi possivel conferir a tabela de preco no WinThor: {}",
                    e.getMostSpecificCause().getMessage());
        }

        // guarda o ultimo preco de cada item como referencia da analise (melhor esforco)
        for (PedidoCompraItem item : pedido.getItens()) {
            try {
                List<UltimaCompra> ultimas = erp.ultimasCompras(item.getCodProdErp(), 1);
                item.setUltimoPreco(ultimas.isEmpty() ? null : ultimas.get(0).precoUnitario());
            } catch (DataAccessException e) {
                log.debug("Sem ultimo preco para {}: {}", item.getCodProdErp(), e.getMessage());
            }
        }

        Usuario usuario = usuarioRepository.getReferenceById(logado.getId());
        String parecer = request == null ? null : request.parecer();
        pedido.setStatus(StatusPedidoCompra.APROVADO);
        pedido.setParecer(parecer == null || parecer.isBlank() ? null : cortar(parecer.trim(), 500));
        pedido.setDataAnalise(OffsetDateTime.now());
        pedido.setUsuarioAnalise(usuario);
        pedido.setStatusIntegracao("PENDENTE");
        pedido.setMensagemErro(null);
        repository.save(pedido);
        registrar(id, usuario, "APROVACAO", "Aprovado pelo fiscal (filial " + pedido.getCodFilial() + ")"
                + (pedido.getParecer() == null ? "." : ": " + pedido.getParecer()));
        return pedido;
    }

    /** Volta um pedido aprovado para analise, desde que a sugestao ainda nao tenha sido gerada. */
    @Transactional
    public PedidoCompra voltarParaAnalise(Long id, UsuarioAutenticado logado) {
        PedidoCompra pedido = buscar(id, logado);
        if (pedido.getStatus() != StatusPedidoCompra.APROVADO) {
            throw new NegocioException("Somente pedidos aprovados podem voltar para analise.");
        }
        if (pedido.isSugestaoGerada()) {
            throw new NegocioException("A sugestao " + pedido.getNumSugestaoErp()
                    + " ja foi gerada no WinThor. Cancele-a na rotina 220 antes.");
        }
        pedido.setStatus(StatusPedidoCompra.EM_ANALISE);
        pedido.setDataAnalise(null);
        pedido.setUsuarioAnalise(null);
        repository.save(pedido);
        registrar(id, usuarioRepository.getReferenceById(logado.getId()), "REABERTURA", "Voltou para analise.");
        return pedido;
    }

    /** Registra o resultado da geracao da sugestao (chamado fora da transacao do ERP). */
    @Transactional
    public PedidoCompra registrarSugestao(Long id, Long numSugestao, String erro, Long usuarioId) {
        PedidoCompra pedido = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido de compra nao encontrado."));
        Usuario usuario = usuarioId == null ? null : usuarioRepository.getReferenceById(usuarioId);
        if (numSugestao != null) {
            pedido.setNumSugestaoErp(numSugestao);
            pedido.setStatusIntegracao("GERADA");
            pedido.setDataIntegracao(OffsetDateTime.now());
            pedido.setMensagemErro(null);
            registrar(id, usuario, "SUGESTAO_GERADA",
                    "Sugestao " + numSugestao + " gerada no WinThor - importar na rotina 220.");
        } else {
            pedido.setStatusIntegracao("ERRO");
            pedido.setMensagemErro(cortar(erro == null ? "Erro desconhecido" : erro, 1000));
            registrar(id, usuario, "ERRO_SUGESTAO", "Falha ao gerar a sugestao: " + pedido.getMensagemErro());
        }
        return repository.save(pedido);
    }

    /** Comparativo de cada item com as ultimas entradas do produto (PCMOV). */
    @Transactional(readOnly = true)
    public ComparativoPrecoResponse comparativo(Long id, UsuarioAutenticado logado) {
        PedidoCompra pedido = buscar(id, logado);
        List<ComparativoPrecoResponse.Item> itens = new ArrayList<>();
        for (PedidoCompraItem item : pedido.getItens()) {
            itens.add(compararItem(item));
        }
        return new ComparativoPrecoResponse(alertaPercentual, itens);
    }

    private ComparativoPrecoResponse.Item compararItem(PedidoCompraItem item) {
        if (item.getCodProdErp() == null) {
            return new ComparativoPrecoResponse.Item(item.getId(), null, item.getPrecoUnitario(),
                    null, null, null, null, "PRODUTO_NOVO", List.of(), null);
        }
        try {
            List<UltimaCompra> ultimas = erp.ultimasCompras(item.getCodProdErp(), qtdHistoricoPrecos);
            if (ultimas.isEmpty()) {
                return new ComparativoPrecoResponse.Item(item.getId(), item.getCodProdErp(),
                        item.getPrecoUnitario(), null, null, null, null, "SEM_HISTORICO", List.of(), null);
            }
            UltimaCompra ultima = ultimas.get(0);
            BigDecimal soma = ultimas.stream().map(UltimaCompra::precoUnitario)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal media = soma.divide(BigDecimal.valueOf(ultimas.size()), 6, RoundingMode.HALF_UP);
            BigDecimal variacao = item.getPrecoUnitario().subtract(ultima.precoUnitario())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(ultima.precoUnitario(), 2, RoundingMode.HALF_UP);
            String situacao = variacao.compareTo(alertaPercentual) > 0 ? "ACIMA"
                    : variacao.compareTo(alertaPercentual.negate()) < 0 ? "ABAIXO" : "OK";
            return new ComparativoPrecoResponse.Item(item.getId(), item.getCodProdErp(), item.getPrecoUnitario(),
                    ultima.precoUnitario(), ultima.data(), media, variacao, situacao, ultimas, null);
        } catch (DataAccessException e) {
            log.warn("Falha ao ler as ultimas compras do produto {}: {}", item.getCodProdErp(),
                    e.getMostSpecificCause().getMessage());
            return new ComparativoPrecoResponse.Item(item.getId(), item.getCodProdErp(), item.getPrecoUnitario(),
                    null, null, null, null, "ERRO", List.of(),
                    "Nao foi possivel ler o historico no WinThor.");
        }
    }

    // ================================================================ apoio

    private Fornecedor fornecedorDoEscopo(UsuarioAutenticado logado, Long fornecedorIdInformado) {
        Long fornecedorId = logado.isAdmin() ? fornecedorIdInformado : logado.getFornecedorId();
        if (fornecedorId == null) {
            throw new NegocioException("Informe o fornecedor do pedido.");
        }
        return fornecedorService.buscar(fornecedorId);
    }

    /** Copia cabecalho e itens da requisicao, conferindo cada produto na origem. */
    private void aplicar(PedidoCompra pedido, PedidoCompraRequest request) {
        Fornecedor fornecedor = pedido.getFornecedor();

        if (fornecedor.getCodFilialCompra() != null) {
            // filial de compra definida no cadastro do fornecedor: vem fixa, ele nao escolhe
            pedido.setCodFilial(fornecedor.getCodFilialCompra());
        } else {
            pedido.setCodFilial(request.codFilial() == null || request.codFilial().isBlank()
                    ? null : validarFilial(request.codFilial().trim()));
        }
        pedido.setTipo(request.tipo() == null ? TipoPedidoCompra.NORMAL : request.tipo());
        if (request.dataPrevisaoEntrega() != null && request.dataPrevisaoEntrega().isBefore(LocalDate.now())) {
            throw new NegocioException("A previsao de entrega nao pode ser uma data passada.");
        }
        pedido.setDataPrevisaoEntrega(request.dataPrevisaoEntrega());
        pedido.setObservacao(request.observacao() == null || request.observacao().isBlank()
                ? null : request.observacao().trim());

        List<PedidoCompraRequest.Item> itens = request.itens() == null ? List.of() : request.itens();

        // produtos do WinThor de uma vez so
        Set<Long> codigos = new LinkedHashSet<>();
        for (PedidoCompraRequest.Item i : itens) {
            if (i.codProdErp() != null) codigos.add(i.codProdErp());
        }
        Map<Long, ProdutoWinthor> winthor = Map.of();
        if (!codigos.isEmpty()) {
            if (fornecedor.getCodFornecedorErp() == null) {
                throw new NegocioException("Fornecedor sem codigo no WinThor: use apenas produtos novos do portal.");
            }
            try {
                winthor = erp.produtosPorCodigo(codigos);
            } catch (DataAccessException e) {
                throw new NegocioException("Nao foi possivel consultar os produtos no WinThor agora. Tente novamente.");
            }
        }

        Set<String> vistos = new HashSet<>();
        List<PedidoCompraItem> novos = new ArrayList<>();
        int linha = 0;
        for (PedidoCompraRequest.Item req : itens) {
            linha++;
            final int numeroLinha = linha;
            PedidoCompraItem item = new PedidoCompraItem();
            if (req.codProdErp() != null) {
                ProdutoWinthor p = winthor.get(req.codProdErp());
                if (p == null) {
                    throw new NegocioException("Item " + linha + ": produto " + req.codProdErp() + " nao encontrado no WinThor.");
                }
                if (!Objects.equals(p.codFornec(), fornecedor.getCodFornecedorErp())) {
                    throw new NegocioException("Item " + linha + ": o produto " + req.codProdErp()
                            + " nao pertence a este fornecedor no WinThor.");
                }
                item.setCodProdErp(p.codProd());
                item.setDescricao(cortar(nvl(p.descricao(), "Produto " + p.codProd()), 150));
                item.setCodigoFabrica(cortar(p.codFab(), 30));
                item.setEmbalagem(cortar(p.embalagem(), 20));
                item.setUnidade(cortar(p.unidade(), 5));
            } else if (req.produtoId() != null) {
                Produto produto = produtoRepository.findById(req.produtoId())
                        .filter(pr -> Objects.equals(pr.getFornecedor().getId(), fornecedor.getId()))
                        .orElseThrow(() -> new NegocioException("Item " + numeroLinha + ": produto novo nao encontrado."));
                item.setProduto(produto);
                item.setDescricao(cortar(produto.getDescricao(), 150));
                item.setCodigoFabrica(cortar(produto.getCodigo(), 30));
                // se ja foi gravado no WinThor, passa a usar o CODPROD
                produtoErpRepository.findById(produto.getId())
                        .map(ProdutoErp::getCodProdErp)
                        .ifPresent(item::setCodProdErp);
            } else {
                throw new NegocioException("Item " + linha + ": selecione o produto.");
            }

            String chave = item.getCodProdErp() != null ? "W" + item.getCodProdErp() : "P" + req.produtoId();
            if (!vistos.add(chave)) {
                throw new NegocioException("O produto " + item.getDescricao()
                        + " aparece mais de uma vez. Some as quantidades numa linha so.");
            }
            item.setQuantidade(req.quantidade());
            item.setPrecoUnitario(req.precoUnitario());
            item.setObservacao(req.observacao() == null || req.observacao().isBlank() ? null : req.observacao().trim());
            novos.add(item);
        }
        pedido.substituirItens(novos);
    }

    private String validarFilial(String codFilial) {
        List<ErpCompraRepository.Filial> filiais = erp.filiais();
        if (!filiais.isEmpty() && filiais.stream().noneMatch(f -> f.codigo().equals(codFilial))) {
            throw new NegocioException("Filial " + codFilial + " nao esta disponivel para pedidos.");
        }
        return codFilial;
    }

    /**
     * "Verificar divergencias no cadastro". Marca cada item e devolve a lista de problemas.
     * Produto novo que entretanto foi gravado no WinThor ganha o CODPROD aqui.
     */
    private List<String> verificarDivergencias(PedidoCompra pedido) {
        Long codFornec = pedido.getFornecedor().getCodFornecedorErp();
        List<String> problemas = new ArrayList<>();

        // produto novo ja integrado passa a ter CODPROD
        for (PedidoCompraItem item : pedido.getItens()) {
            if (item.getCodProdErp() == null && item.getProduto() != null) {
                produtoErpRepository.findById(item.getProduto().getId())
                        .map(ProdutoErp::getCodProdErp)
                        .ifPresent(item::setCodProdErp);
            }
        }

        Map<Long, ProdutoWinthor> winthor;
        try {
            winthor = erp.produtosPorCodigo(pedido.getItens().stream()
                    .map(PedidoCompraItem::getCodProdErp).filter(Objects::nonNull).toList());
        } catch (DataAccessException e) {
            throw new NegocioException("Nao foi possivel consultar o WinThor para conferir o cadastro. Tente novamente.");
        }

        // a 220 recusa produto sem tabela de preco/tributacao na regiao da filial
        Set<Long> semTabela = Set.of();
        try {
            semTabela = erp.semTabelaPreco(winthor.keySet(), pedido.getCodFilial());
        } catch (DataAccessException e) {
            log.warn("Nao foi possivel conferir a tabela de preco no WinThor: {}",
                    e.getMostSpecificCause().getMessage());
        }

        for (PedidoCompraItem item : pedido.getItens()) {
            String divergencia = null;
            if (item.getCodProdErp() == null) {
                divergencia = textoProdutoNovo(item.getProduto());
            } else {
                ProdutoWinthor p = winthor.get(item.getCodProdErp());
                if (p == null) {
                    divergencia = "Produto nao encontrado no WinThor.";
                } else if (codFornec == null) {
                    divergencia = "Fornecedor sem codigo no WinThor.";
                } else if (!Objects.equals(p.codFornec(), codFornec)) {
                    divergencia = "Produto cadastrado no WinThor para outro fornecedor (" + p.codFornec() + ").";
                } else if (p.foraDeLinha()) {
                    divergencia = "Produto fora de linha no WinThor.";
                } else if (semTabela.contains(p.codProd())) {
                    divergencia = textoSemTabela(pedido.getCodFilial());
                }
            }
            item.setDivergencia(divergencia);
            if (divergencia != null) {
                problemas.add(item.getDescricao() + ": " + divergencia);
            }
        }
        return problemas;
    }

    private static String textoSemTabela(String codFilial) {
        return "Sem tabela de preco/tributacao na filial " + codFilial
                + " - a rotina 220 recusa. Abra o produto na rotina 203, edite e salve.";
    }

    private String textoProdutoNovo(Produto produto) {
        if (produto == null) {
            return "Produto sem cadastro.";
        }
        return switch (produto.getStatus()) {
            case RASCUNHO -> "Produto novo: o cadastro ainda nao foi enviado para analise.";
            case ENVIADO -> "Produto novo: cadastro aguardando analise.";
            case APROVADO -> "Produto novo: cadastro aprovado, falta gravar no WinThor.";
            case REPROVADO -> "Produto novo: cadastro devolvido para correcao.";
        };
    }

    private void registrar(Long pedidoId, Usuario usuario, String evento, String descricao) {
        historicoRepository.save(PedidoCompraHistorico.de(pedidoId, usuario, evento, descricao));
    }

    private static String rotulo(StatusPedidoCompra status) {
        return switch (status) {
            case RASCUNHO -> "em digitacao";
            case DIVERGENCIA -> "com divergencia de cadastro em analise";
            case EM_ANALISE -> "em analise pelo fiscal";
            case REVISAO -> "devolvido para revisao";
            case REPROVADO -> "reprovado";
            case APROVADO -> "aprovado";
            case CANCELADO -> "cancelado";
        };
    }

    private static String nvl(String v, String padrao) {
        return v == null || v.isBlank() ? padrao : v.trim();
    }

    private static String cortar(String v, int max) {
        if (v == null) return null;
        return v.length() <= max ? v : v.substring(0, max);
    }
}
