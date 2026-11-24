package br.com.grupopapa.cfp.systock;

import br.com.grupopapa.cfp.domain.Fornecedor;
import br.com.grupopapa.cfp.domain.PedidoCompra;
import br.com.grupopapa.cfp.domain.PedidoCompraItem;
import br.com.grupopapa.cfp.domain.StatusPedidoCompra;
import br.com.grupopapa.cfp.domain.TipoPedidoCompra;
import br.com.grupopapa.cfp.dto.PedidoCompraRequest;
import br.com.grupopapa.cfp.exception.NegocioException;
import br.com.grupopapa.cfp.exception.RecursoNaoEncontradoException;
import br.com.grupopapa.cfp.repository.ErpCompraRepository;
import br.com.grupopapa.cfp.repository.ErpCompraRepository.ProdutoWinthor;
import br.com.grupopapa.cfp.repository.ErpCompraRepository.UltimaCompra;
import br.com.grupopapa.cfp.repository.FornecedorRepository;
import br.com.grupopapa.cfp.repository.PedidoCompraRepository;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import br.com.grupopapa.cfp.service.FornecedorService;
import br.com.grupopapa.cfp.service.PedidoCompraService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Sugestoes de compra do Systock no portal.
 *
 * O Systock nao recebe dados de terceiros (a API so aceita gravar o id do pedido do ERP e
 * cancelar), entao o fluxo e de mao unica: o portal LE os pedidos do Systock, mostra ao
 * fornecedor os que sao dele e, quando ele quer, cria um pedido de compra em digitacao no
 * portal com os mesmos itens. Dali em diante segue o fluxo normal do portal (edicao, envio,
 * analise fiscal, sugestao na PCSUGESTAOCOMPRAC). O pedido guarda o numero da sugestao do
 * Systock (SYSTOCK_ID) para nao ser trazido duas vezes.
 *
 * O fornecedor do Systock (id_fornecedor) e o CODFORNEC do WinThor (CFP_FORNECEDOR.COD_FORNEC_ERP).
 */
@Service
public class SystockService {

    private static final Logger log = LoggerFactory.getLogger(SystockService.class);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Status consultados no Systock, na ordem em que aparecem na tela. Cancelados (CL) ficam de fora. */
    static final Map<String, String> STATUS = new LinkedHashMap<>();
    static {
        STATUS.put("AB", "Em aberto");
        STATUS.put("CT", "Em cotacao");
        STATUS.put("PARA_EXPORTACAO", "Para exportacao");
        STATUS.put("FN", "Finalizado");
    }

    private final SystockClient client;
    private final PedidoCompraService compraService;
    private final PedidoCompraRepository pedidoRepository;
    private final FornecedorService fornecedorService;
    private final FornecedorRepository fornecedorRepository;
    private final ErpCompraRepository erp;
    private final long cacheMillis;
    private final int diasFinalizados;

    private volatile Cache cache;

    private record Marcado(String statusCodigo, SystockPedido pedido) { }

    private record Cache(Instant em, List<Marcado> pedidos) { }

    public SystockService(SystockClient client, PedidoCompraService compraService,
                          PedidoCompraRepository pedidoRepository, FornecedorService fornecedorService,
                          FornecedorRepository fornecedorRepository, ErpCompraRepository erp,
                          @Value("${app.systock.cache-segundos:60}") long cacheSegundos,
                          @Value("${app.systock.dias-finalizados:90}") int diasFinalizados) {
        this.client = client;
        this.compraService = compraService;
        this.pedidoRepository = pedidoRepository;
        this.fornecedorService = fornecedorService;
        this.fornecedorRepository = fornecedorRepository;
        this.erp = erp;
        this.cacheMillis = Math.max(cacheSegundos, 0) * 1000;
        this.diasFinalizados = Math.max(diasFinalizados, 1);
    }

    // ================================================================ consulta

    @Transactional(readOnly = true)
    public SugestoesSystockResponse listar(UsuarioAutenticado logado, Long fornecedorIdInformado, boolean atualizar) {
        if (!client.isHabilitado()) {
            return SugestoesSystockResponse.desligado();
        }
        Long codErp = null;
        if (!logado.isAdmin() || fornecedorIdInformado != null) {
            Fornecedor f = fornecedorService.buscar(logado.isAdmin() ? fornecedorIdInformado : logado.getFornecedorId());
            codErp = f.getCodFornecedorErp();
            if (codErp == null) {
                return SugestoesSystockResponse.comAviso(diasFinalizados,
                        "Fornecedor sem codigo no WinThor: nao da para localizar as sugestoes dele no Systock.");
            }
        }

        List<Marcado> todos;
        try {
            todos = pedidos(atualizar);
        } catch (SystockClient.SystockIndisponivelException e) {
            return SugestoesSystockResponse.comAviso(diasFinalizados,
                    "Nao foi possivel consultar o Systock agora. Tente novamente em instantes.");
        }

        LocalDate corte = LocalDate.now().minusDays(diasFinalizados);
        final Long filtro = codErp;
        List<Marcado> escolhidos = todos.stream()
                .filter(m -> filtro == null || Objects.equals(m.pedido().idFornecedor(), filtro))
                .filter(m -> !"FN".equals(m.statusCodigo())
                        || m.pedido().dataAbertura() == null || !m.pedido().dataAbertura().isBefore(corte))
                .sorted(Comparator.comparing((Marcado m) -> ordemStatus(m.statusCodigo()))
                        .thenComparing(m -> m.pedido().dataAbertura(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(m -> m.pedido().idPedido(), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        // descricoes dos produtos no WinThor
        Set<Long> codigos = new HashSet<>();
        escolhidos.forEach(m -> m.pedido().produtosOuVazio().forEach(p -> {
            Long c = numero(p.idProduto());
            if (c != null) codigos.add(c);
        }));
        Map<Long, ProdutoWinthor> produtos = Map.of();
        String aviso = null;
        if (!codigos.isEmpty() && codigos.size() <= 5000) {
            try {
                produtos = erp.produtosPorCodigo(codigos);
            } catch (DataAccessException e) {
                log.warn("Falha ao buscar descricoes no WinThor: {}", e.getMostSpecificCause().getMessage());
                aviso = "As descricoes dos produtos nao puderam ser carregadas do WinThor agora.";
            }
        }
        // grupo de cada fornecedor no WinThor (principal + secundarios)
        Map<Long, Map<Long, String>> grupos = new HashMap<>();
        Map<Long, Set<Long>> codigosPorFornecedor = new HashMap<>();
        for (Marcado m : escolhidos) {
            Long f = m.pedido().idFornecedor();
            if (f == null) continue;
            if (!grupos.containsKey(f)) grupos.put(f, erp.fornecedoresDoGrupo(f));
            Set<Long> cods = codigosPorFornecedor.computeIfAbsent(f, k -> new HashSet<>());
            m.pedido().produtosOuVazio().forEach(p -> {
                Long c = numero(p.idProduto());
                if (c != null) cods.add(c);
            });
        }

        // ultima entrada de cada produto vinda do fornecedor da sugestao (ou do grupo dele)
        Map<Long, Map<Long, UltimaCompra>> ultimasPorFornecedor = new HashMap<>();
        if (codigos.size() <= 5000) {
            try {
                for (Map.Entry<Long, Set<Long>> e : codigosPorFornecedor.entrySet()) {
                    if (e.getValue().isEmpty()) continue;
                    ultimasPorFornecedor.put(e.getKey(), erp.ultimaCompraPorProduto(e.getValue(), e.getKey(),
                            grupos.getOrDefault(e.getKey(), Map.of()).keySet()));
                }
            } catch (DataAccessException e) {
                log.warn("Falha ao buscar ultimas entradas no WinThor: {}", e.getMostSpecificCause().getMessage());
                if (aviso == null) aviso = "O valor da ultima entrada nao pode ser carregado do WinThor agora.";
            }
        }

        // ja trazidas para o portal
        Map<Long, PedidoCompra> importados = new HashMap<>();
        List<Long> ids = escolhidos.stream().map(m -> m.pedido().idPedido()).filter(Objects::nonNull).toList();
        for (int i = 0; i < ids.size(); i += 500) {
            for (PedidoCompra p : pedidoRepository.importadosDoSystock(
                    ids.subList(i, Math.min(i + 500, ids.size())), StatusPedidoCompra.CANCELADO)) {
                importados.put(p.getSystockId(), p);
            }
        }

        // fornecedor do portal (para a equipe interna, que ve todos)
        Map<Long, Fornecedor> fornecedoresPorCod = new HashMap<>();
        if (logado.isAdmin()) {
            for (Fornecedor f : fornecedorService.listarTodos()) {
                if (f.getCodFornecedorErp() != null) fornecedoresPorCod.putIfAbsent(f.getCodFornecedorErp(), f);
            }
        }

        List<SugestoesSystockResponse.Sugestao> lista = new ArrayList<>();
        for (Marcado m : escolhidos) {
            lista.add(montar(m, produtos,
                    ultimasPorFornecedor.getOrDefault(m.pedido().idFornecedor(), Map.of()),
                    importados.get(m.pedido().idPedido()),
                    fornecedoresPorCod.get(m.pedido().idFornecedor()),
                    grupos.getOrDefault(m.pedido().idFornecedor(), Map.of())));
        }
        return new SugestoesSystockResponse(true, aviso, diasFinalizados, lista);
    }

    private SugestoesSystockResponse.Sugestao montar(Marcado m, Map<Long, ProdutoWinthor> produtos,
                                                     Map<Long, UltimaCompra> ultimas, PedidoCompra importado, Fornecedor fornecedorPortal,
                                                     Map<Long, String> grupo) {
        SystockPedido sp = m.pedido();
        List<SugestoesSystockResponse.Item> itens = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (SystockPedido.Produto p : sp.produtosOuVazio()) {
            Long cod = numero(p.idProduto());
            ProdutoWinthor w = cod == null ? null : produtos.get(cod);
            BigDecimal valor = p.quantidade() != null && p.preco() != null
                    ? p.quantidade().multiply(p.preco()).setScale(2, RoundingMode.HALF_UP) : null;
            if (valor != null) total = total.add(valor);
            UltimaCompra u = cod == null ? null : ultimas.get(cod);
            boolean outroDoGrupo = w != null && w.codFornec() != null && sp.idFornecedor() != null
                    && !Objects.equals(w.codFornec(), sp.idFornecedor()) && grupo.containsKey(w.codFornec());
            String fornecedorProduto = outroDoGrupo
                    ? w.codFornec() + (grupo.get(w.codFornec()) == null ? "" : " - " + grupo.get(w.codFornec()).trim())
                    : null;
            itens.add(new SugestoesSystockResponse.Item(p.item(), p.idProduto(), cod,
                    w == null ? null : w.descricao(), w == null ? null : w.codFab(), w == null ? null : w.embalagem(),
                    p.quantidadeSugerida(), p.quantidade(), p.preco(), valor,
                    u == null ? null : new SugestoesSystockResponse.UltimaEntrada(u.data(), u.precoUnitario(),
                            u.quantidade(), u.numNota(), u.codFilial(), u.codFornec(), u.fornecedor()),
                    w != null && sp.idFornecedor() != null && !Objects.equals(w.codFornec(), sp.idFornecedor())
                            && !outroDoGrupo,
                    fornecedorProduto));
        }
        return new SugestoesSystockResponse.Sugestao(
                sp.idPedido(), m.statusCodigo(), STATUS.getOrDefault(m.statusCodigo(), sp.status()), sp.status(),
                sp.dataAbertura(), primeiro(sp.nomeComprador(), sp.nomeUsuario()),
                sp.idFornecedor(), sp.nomeFornecedor(), fornecedorPortal == null ? null : fornecedorPortal.getId(),
                sp.idFilial(), sp.observacao(), sp.condicaoPagamento(),
                itens.size(), total, itens,
                importado == null ? null : new SugestoesSystockResponse.Importado(
                        importado.getId(), importado.getNumero(), importado.getStatus()));
    }

    /** Pedidos do Systock nos status de interesse, com cache curto para nao repetir a consulta a cada tela. */
    private List<Marcado> pedidos(boolean atualizar) {
        Cache c = cache;
        if (!atualizar && c != null && cacheMillis > 0
                && c.em().plusMillis(cacheMillis).isAfter(Instant.now())) {
            return c.pedidos();
        }
        List<Marcado> todos = new ArrayList<>();
        for (String status : STATUS.keySet()) {
            for (SystockPedido p : client.pedidos(List.of(status))) {
                todos.add(new Marcado(status, p));
            }
        }
        cache = new Cache(Instant.now(), List.copyOf(todos));
        return todos;
    }

    // ================================================================ trazer para o portal

    /**
     * Cria no portal um pedido de compra em digitacao com os itens da sugestao do Systock.
     * O fornecedor edita e envia pelo fluxo normal. A sugestao fica marcada como trazida.
     */
    @Transactional
    public PedidoCompra importar(long idSystock, UsuarioAutenticado logado) {
        if (!client.isHabilitado()) {
            throw new NegocioException("A consulta ao Systock nao esta configurada no portal.");
        }
        SystockPedido sp;
        try {
            sp = client.pedido(idSystock)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Sugestao " + idSystock + " nao encontrada no Systock."));
        } catch (SystockClient.SystockIndisponivelException e) {
            throw new NegocioException("Nao foi possivel consultar o Systock agora. Tente novamente em instantes.");
        }

        Fornecedor fornecedor;
        if (logado.isAdmin()) {
            fornecedor = sp.idFornecedor() == null ? null
                    : fornecedorRepository.findByCodFornecedorErp(sp.idFornecedor()).orElse(null);
            if (fornecedor == null) {
                throw new NegocioException("O fornecedor " + sp.idFornecedor() + " - " + sp.nomeFornecedor()
                        + " ainda nao esta cadastrado no portal. Importe-o do WinThor em Fornecedores.");
            }
        } else {
            fornecedor = fornecedorService.buscar(logado.getFornecedorId());
            if (fornecedor.getCodFornecedorErp() == null
                    || !Objects.equals(fornecedor.getCodFornecedorErp(), sp.idFornecedor())) {
                throw new RecursoNaoEncontradoException("Sugestao " + idSystock + " nao encontrada.");
            }
        }

        if (sp.status() != null && sp.status().toUpperCase(Locale.ROOT).startsWith("CANCEL")) {
            throw new NegocioException("A sugestao " + idSystock + " foi cancelada no Systock.");
        }
        List<PedidoCompra> ja = pedidoRepository.importadosDoSystock(List.of(idSystock), StatusPedidoCompra.CANCELADO);
        if (!ja.isEmpty()) {
            throw new NegocioException("A sugestao " + idSystock + " ja foi trazida para o portal no pedido "
                    + ja.get(0).getNumero() + ".");
        }

        List<PedidoCompraRequest.Item> itens = itens(sp);
        if (itens.size() > 300) {
            throw new NegocioException("A sugestao tem " + itens.size() + " produtos; o pedido do portal aceita no maximo 300.");
        }

        StringBuilder obs = new StringBuilder("Sugestao de compra Systock n. ").append(idSystock);
        if (sp.dataAbertura() != null) obs.append(", aberta em ").append(sp.dataAbertura().format(DATA));
        String quem = primeiro(sp.nomeComprador(), sp.nomeUsuario());
        if (quem != null) obs.append(" por ").append(quem.trim());
        obs.append('.');
        if (sp.condicaoPagamento() != null && !sp.condicaoPagamento().isBlank()) {
            obs.append(" Cond. pagamento: ").append(sp.condicaoPagamento().trim()).append('.');
        }
        if (sp.observacao() != null && !sp.observacao().isBlank()) obs.append(' ').append(sp.observacao().trim());
        String observacao = obs.length() > 500 ? obs.substring(0, 500) : obs.toString();

        String codFilial = sp.idFilial() == null ? null : String.valueOf(sp.idFilial());
        PedidoCompra pedido = compraService.criar(
                new PedidoCompraRequest(codFilial, TipoPedidoCompra.NORMAL, null, observacao, itens),
                logado, fornecedor.getId());
        pedido.setSystockId(idSystock);
        // guarda nos itens o que veio do Systock (quantidade sugerida, quantidade e preco originais)
        Map<Long, BigDecimal[]> origem = new HashMap<>();
        for (SystockPedido.Produto p : sp.produtosOuVazio()) {
            Long cod = numero(p.idProduto());
            if (cod == null) continue;
            BigDecimal[] o = origem.computeIfAbsent(cod, c -> new BigDecimal[]{null, null, p.preco()});
            if (p.quantidadeSugerida() != null) o[0] = o[0] == null ? p.quantidadeSugerida() : o[0].add(p.quantidadeSugerida());
            if (p.quantidade() != null) o[1] = o[1] == null ? p.quantidade() : o[1].add(p.quantidade());
        }
        for (PedidoCompraItem item : pedido.getItens()) {
            BigDecimal[] o = item.getCodProdErp() == null ? null : origem.get(item.getCodProdErp());
            if (o != null) {
                item.setQtdeSugerida(o[0]);
                item.setQtdeSystock(o[1]);
                item.setPrecoSystock(o[2]);
            }
        }
        return pedidoRepository.save(pedido);
    }

    /** Itens da sugestao; o mesmo produto repetido com o mesmo preco vira uma linha so. */
    private List<PedidoCompraRequest.Item> itens(SystockPedido sp) {
        Map<Long, BigDecimal[]> porProduto = new LinkedHashMap<>();
        List<String> problemas = new ArrayList<>();
        for (SystockPedido.Produto p : sp.produtosOuVazio()) {
            String rot = "Item " + (p.item() == null ? "?" : p.item()) + " (produto " + p.idProduto() + ")";
            Long cod = numero(p.idProduto());
            if (cod == null) {
                problemas.add(rot + ": codigo de produto invalido.");
                continue;
            }
            if (p.quantidade() == null || p.quantidade().signum() <= 0) {
                problemas.add(rot + ": sem quantidade.");
                continue;
            }
            if (p.preco() == null || p.preco().signum() <= 0) {
                problemas.add(rot + ": sem preco.");
                continue;
            }
            BigDecimal[] atual = porProduto.get(cod);
            if (atual == null) {
                porProduto.put(cod, new BigDecimal[]{p.quantidade(), p.preco()});
            } else if (atual[1].compareTo(p.preco()) == 0) {
                atual[0] = atual[0].add(p.quantidade());
            } else {
                problemas.add(rot + ": produto repetido na sugestao com precos diferentes.");
            }
        }
        if (!problemas.isEmpty()) {
            throw new NegocioException("A sugestao " + sp.idPedido() + " nao pode ser trazida: "
                    + String.join(" ", problemas) + " Ajuste no Systock ou digite o pedido no portal.");
        }
        if (porProduto.isEmpty()) {
            throw new NegocioException("A sugestao " + sp.idPedido() + " nao tem produtos.");
        }
        List<PedidoCompraRequest.Item> itens = new ArrayList<>();
        porProduto.forEach((cod, v) -> itens.add(new PedidoCompraRequest.Item(
                cod, null, v[0].stripTrailingZeros(), v[1].stripTrailingZeros(), null)));
        return itens;
    }

    // ================================================================ apoio

    private static int ordemStatus(String s) {
        int i = 0;
        for (String k : STATUS.keySet()) {
            if (k.equals(s)) return i;
            i++;
        }
        return i;
    }

    private static Long numero(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.matches("\\d{1,15}") ? Long.valueOf(t) : null;
    }

    private static String primeiro(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }
}
