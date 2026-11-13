package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.Fornecedor;
import br.com.grupopapa.cfp.domain.Produto;
import br.com.grupopapa.cfp.dto.ConferenciaCodigosResponse;
import br.com.grupopapa.cfp.dto.ConferenciaCodigosResponse.Conflito;
import br.com.grupopapa.cfp.repository.ErpCodigosProdutoRepository;
import br.com.grupopapa.cfp.repository.ErpCodigosProdutoRepository.ProdutoComCodigo;
import br.com.grupopapa.cfp.repository.ProdutoErpRepository;
import br.com.grupopapa.cfp.repository.ProdutoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Confere EAN, DUN e codigo de fabrica do cadastro feito pelo fornecedor:
 *  - no portal: codigo de fabrica unico por fornecedor; EAN e DUN unicos (nem como EAN nem como DUN
 *    de outro produto); DUN diferente do EAN;
 *  - no WinThor (PCPRODUT): EAN e DUN que ja existem em outro produto (CODAUXILIAR/CODAUXILIAR2) e
 *    codigo de fabrica ja usado pelo mesmo fornecedor (CODFAB).
 * Se o WinThor nao responder, so a parte do portal e conferida (nao trava o fornecedor).
 */
@Service
public class ConferenciaCodigosService {

    private static final Logger log = LoggerFactory.getLogger(ConferenciaCodigosService.class);

    private final ProdutoRepository produtoRepository;
    private final ProdutoErpRepository produtoErpRepository;
    private final ErpCodigosProdutoRepository erp;

    public ConferenciaCodigosService(ProdutoRepository produtoRepository,
                                     ProdutoErpRepository produtoErpRepository,
                                     ErpCodigosProdutoRepository erp) {
        this.produtoRepository = produtoRepository;
        this.produtoErpRepository = produtoErpRepository;
        this.erp = erp;
    }

    @Transactional(readOnly = true)
    public ConferenciaCodigosResponse conferir(Fornecedor fornecedor, String codigo, String ean, String dun,
                                               Long produtoId) {
        List<Conflito> conflitos = new ArrayList<>();
        String cod = limpar(codigo);
        String e = digitos(ean);
        String d = digitos(dun);

        // ------------------------------------------------ portal
        if (cod != null) {
            boolean repetido = produtoId == null
                    ? produtoRepository.existsByFornecedorIdAndCodigoIgnoreCase(fornecedor.getId(), cod)
                    : produtoRepository.existsByFornecedorIdAndCodigoIgnoreCaseAndIdNot(fornecedor.getId(), cod, produtoId);
            if (repetido) {
                conflitos.add(new Conflito("codigo",
                        "Ja existe um produto com o codigo de fabrica " + cod + " cadastrado pelo portal para este fornecedor."));
            }
        }
        if (e != null && e.equals(d)) {
            conflitos.add(new Conflito("codBarraCaixa", "O DUN da caixa master deve ser diferente do EAN do produto."));
        }
        if (e != null) portal(e, "EAN", "codBarraProduto", produtoId, conflitos);
        if (d != null && !d.equals(e)) portal(d, "DUN", "codBarraCaixa", produtoId, conflitos);

        // ------------------------------------------------ WinThor
        boolean consultado = true;
        try {
            Long proprio = produtoId == null ? null
                    : produtoErpRepository.codProdDoProduto(produtoId).stream().findFirst().orElse(null);
            if (e != null) winthor(e, "EAN", "codBarraProduto", proprio, conflitos);
            if (d != null && !d.equals(e)) winthor(d, "DUN", "codBarraCaixa", proprio, conflitos);
            if (cod != null && fornecedor.getCodFornecedorErp() != null) {
                List<ProdutoComCodigo> fab = erp.comCodigoFabrica(fornecedor.getCodFornecedorErp(), cod, proprio);
                if (!fab.isEmpty()) {
                    ProdutoComCodigo p = fab.get(0);
                    conflitos.add(new Conflito("codigo", "O codigo de fabrica " + cod
                            + " ja esta cadastrado no WinThor para este fornecedor: produto " + p.codProd()
                            + " - " + p.descricao() + "."));
                }
            }
        } catch (DataAccessException ex) {
            consultado = false;
            log.warn("Conferencia de EAN/DUN/codigo de fabrica no WinThor indisponivel: {}",
                    ex.getMostSpecificCause().getMessage());
        }
        return new ConferenciaCodigosResponse(conflitos, consultado);
    }

    private void portal(String codigo, String rotulo, String campo, Long produtoId, List<Conflito> conflitos) {
        List<Produto> outros = produtoRepository.comCodigoDeBarras(codigo, produtoId);
        if (outros.isEmpty()) return;
        Produto p = outros.get(0);
        String comoQue = codigo.equals(p.getCodBarraProduto()) ? "EAN" : "DUN";
        conflitos.add(new Conflito(campo, "O " + rotulo + " " + codigo + " ja esta cadastrado pelo portal como "
                + comoQue + " do produto " + p.getCodigo() + " - " + p.getDescricao()
                + " (" + p.getFornecedor().getRazaoSocial() + ")."));
    }

    private void winthor(String codigo, String rotulo, String campo, Long proprio, List<Conflito> conflitos) {
        List<ProdutoComCodigo> achados = erp.comCodigoDeBarras(Long.parseLong(codigo), proprio);
        if (achados.isEmpty()) return;
        ProdutoComCodigo p = achados.get(0);
        conflitos.add(new Conflito(campo, "O " + rotulo + " " + codigo + " ja esta cadastrado no WinThor como "
                + p.campo() + " do produto " + p.codProd() + " - " + p.descricao()
                + (p.fornecedor() != null ? " (" + p.fornecedor() + ")" : "") + "."));
    }

    private static String limpar(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** So considera codigo de barras com 8 a 14 digitos (o formato ja e validado no formulario). */
    private static String digitos(String v) {
        if (v == null) return null;
        String d = v.trim();
        return d.matches("\\d{8,14}") ? d : null;
    }
}
