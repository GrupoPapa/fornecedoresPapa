package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.Produto;
import br.com.grupopapa.cfp.domain.ProdutoErp;
import br.com.grupopapa.cfp.dto.ProdutoErpRequest;
import br.com.grupopapa.cfp.repository.ProdutoErpRepository;
import br.com.grupopapa.cfp.repository.UsuarioRepository;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProdutoErpService {

    private final ProdutoErpRepository repository;
    private final ProdutoService produtoService;
    private final UsuarioRepository usuarioRepository;
    private final boolean exigirComplemento;
    private final boolean prePreencher;

    public ProdutoErpService(ProdutoErpRepository repository,
                             ProdutoService produtoService,
                             UsuarioRepository usuarioRepository,
                             @Value("${app.erp.exigir-complemento:true}") boolean exigirComplemento,
                             @Value("${app.erp.pre-preencher:false}") boolean prePreencher) {
        this.repository = repository;
        this.produtoService = produtoService;
        this.usuarioRepository = usuarioRepository;
        this.exigirComplemento = exigirComplemento;
        this.prePreencher = prePreencher;
    }

    public boolean isExigirComplemento() {
        return exigirComplemento;
    }

    /**
     * Devolve o complemento ja salvo. Se ainda nao existir, devolve um bloco em branco
     * para a analista preencher manualmente (comportamento atual) ou, se
     * app.erp.pre-preencher = true, uma sugestao montada com o que o fornecedor informou.
     */
    @Transactional(readOnly = true)
    public Resultado buscar(Long produtoId, UsuarioAutenticado logado) {
        Produto produto = produtoService.buscar(produtoId, logado);
        return repository.findById(produtoId)
                .map(e -> new Resultado(e, true))
                .orElseGet(() -> new Resultado(prePreencher ? sugestao(produto) : vazio(produto), false));
    }

    @Transactional
    public Resultado salvar(Long produtoId, ProdutoErpRequest request, UsuarioAutenticado logado) {
        Produto produto = produtoService.buscar(produtoId, logado);
        ProdutoErp erp = repository.findById(produtoId).orElseGet(() -> {
            ProdutoErp novo = new ProdutoErp();
            novo.setProduto(produto);
            return novo;
        });

        if ("INTEGRADO".equals(erp.getStatusIntegracao())) {
            throw new br.com.grupopapa.cfp.exception.NegocioException(
                    "Este produto ja foi gravado no WinThor (codigo " + erp.getCodProdErp() + ").");
        }

        aplicar(erp, request);
        erp.setUsuario(usuarioRepository.getReferenceById(logado.getId()));
        return new Resultado(repository.save(erp), true);
    }

    @Transactional(readOnly = true)
    public List<ProdutoErp> porPedido(Long pedidoId) {
        return repository.listarPorPedido(pedidoId);
    }

    // ------------------------------------------------------------------ apoio

    /** Bloco totalmente em branco: todos os campos ficam a cargo da analista. */
    private ProdutoErp vazio(Produto p) {
        ProdutoErp e = new ProdutoErp();
        e.setProduto(p);
        return e;
    }

    /**
     * Pre-preenche com o que veio do fornecedor e com os padroes combinados.
     * So e usado quando app.erp.pre-preencher = true (CFP_ERP_PRE_PREENCHER).
     */
    private ProdutoErp sugestao(Produto p) {
        ProdutoErp e = new ProdutoErp();
        e.setProduto(p);
        e.setDescricao(cortar(p.getDescricao(), 40));   // PCPRODUT.DESCRICAO e VARCHAR2(40)
        e.setCodFab(cortar(p.getCodigo(), 30));
        e.setClassificacaoFiscal(p.getNcm());
        e.setCodAuxiliar(p.getCodBarraProduto());
        e.setGtinCodAuxiliar(padraoGtin(p.getCodBarraProduto()));
        e.setPesoLiq(p.getPesoLiquido());
        e.setPesoBruto(p.getPesoBruto());
        e.setQtUnitCx(p.getQtdEmbalagem() != null
                ? new BigDecimal(p.getQtdEmbalagem()) : p.getM2Caixa());
        e.setQtUnit(BigDecimal.ONE);
        e.setMultiplo(BigDecimal.ONE);
        e.setUnidade("UN");
        e.setUnidadeMaster("CX");
        e.setEmbalagem("UN");
        e.setPComExt1(new BigDecimal("2"));
        e.setPComInt1(new BigDecimal("0.8"));
        e.setPComRep1(new BigDecimal("1.5"));
        e.setRevenda("S");
        e.setTemRepos(30);   // tempo de reposicao em dias - 30 e o padrao da casa
        e.setCestaBasicaLegis("N");
        e.setAceitaVendaFracao("N");
        e.setEnviaInfTecnicaNfe("N");
        e.setEnviaEcommerce("N");
        e.setUsaWms("N");
        e.setNaturezaProduto("OT");
        e.setTipoCargaProduto("G");
        e.setLocalSeparacao("I");
        e.setSugVenda(0);
        if (p.getFornecedor() != null && p.getFornecedor().getTipo().isPiso()) {
            e.setUnidade("M2");
            e.setEmbalagem("M2");
        }
        return e;
    }

    private void aplicar(ProdutoErp e, ProdutoErpRequest r) {
        e.setDescricao(texto(r.descricao()));
        e.setCodMarca(r.codMarca());
        e.setCodEpto(r.codEpto());
        e.setCodSec(r.codSec());
        e.setCodFab(texto(r.codFab()));
        e.setCodNcmEx(texto(r.codNcmEx()));
        e.setClassificacaoFiscal(texto(r.classificacaoFiscal()));
        e.setEmbalagem(maiusculo(r.embalagem()));
        e.setUnidade(maiusculo(r.unidade()));
        e.setQtUnit(r.qtUnit());
        e.setUnidadeMaster(maiusculo(r.unidadeMaster()));
        e.setQtUnitCx(r.qtUnitCx());
        e.setMultiplo(r.multiplo());
        e.setPesoLiq(r.pesoLiq());
        e.setPesoBruto(r.pesoBruto());
        e.setCodAuxiliar(texto(r.codAuxiliar()));
        // se a analista nao escolher, o padrao do GTIN sai do tamanho do codigo de barras
        e.setGtinCodAuxiliar(r.gtinCodAuxiliar() != null
                ? r.gtinCodAuxiliar() : padraoGtin(texto(r.codAuxiliar())));
        e.setSugVenda(r.sugVenda());
        e.setPComExt1(r.pComExt1());
        e.setPComInt1(r.pComInt1());
        e.setPComRep1(r.pComRep1());
        e.setRevenda(maiusculo(r.revenda()));
        e.setTemRepos(r.temRepos());
        e.setCestaBasicaLegis(maiusculo(r.cestaBasicaLegis()));
        e.setAceitaVendaFracao(maiusculo(r.aceitaVendaFracao()));
        e.setEnviaInfTecnicaNfe(maiusculo(r.enviaInfTecnicaNfe()));
        e.setEnviaEcommerce(maiusculo(r.enviaEcommerce()));
        e.setUsaWms(maiusculo(r.usaWms()));
        e.setTipoCargaProduto(maiusculo(r.tipoCargaProduto()));
        e.setLocalSeparacao(maiusculo(r.localSeparacao()));
        e.setNaturezaProduto(maiusculo(r.naturezaProduto()));
    }

    private String texto(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private String maiusculo(String v) {
        String t = texto(v);
        return t == null ? null : t.toUpperCase();
    }

    /**
     * PCPRODUT.GTINCODAUXILIAR guarda o PADRAO do codigo de barras (GTIN-8, 12, 13 ou 14),
     * e nao o codigo em si - por isso a coluna e NUMBER(2). Deduz pelo tamanho.
     */
    private Integer padraoGtin(String codigoBarras) {
        String c = texto(codigoBarras);
        if (c == null) return null;
        int n = c.length();
        return (n == 8 || n == 12 || n == 13 || n == 14) ? n : null;
    }

    private String cortar(String v, int max) {
        if (v == null) return null;
        return v.length() <= max ? v : v.substring(0, max);
    }

    public record Resultado(ProdutoErp erp, boolean salvo) { }
}
