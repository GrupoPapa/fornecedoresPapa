package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.Fornecedor;
import br.com.grupopapa.cfp.domain.Produto;
import br.com.grupopapa.cfp.dto.CatalogoProdutosResponse;
import br.com.grupopapa.cfp.exception.NegocioException;
import br.com.grupopapa.cfp.repository.ErpProdutoConsultaRepository;
import br.com.grupopapa.cfp.repository.ProdutoNovoRepository;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Junta os produtos do fornecedor no WinThor com os novos cadastrados pelo portal. */
@Service
public class CatalogoProdutoService {

    private static final Logger log = LoggerFactory.getLogger(CatalogoProdutoService.class);

    private final ErpProdutoConsultaRepository erp;
    private final ProdutoNovoRepository novos;
    private final FornecedorService fornecedorService;

    public CatalogoProdutoService(ErpProdutoConsultaRepository erp, ProdutoNovoRepository novos,
                                  FornecedorService fornecedorService) {
        this.erp = erp;
        this.novos = novos;
        this.fornecedorService = fornecedorService;
    }

    @Transactional(readOnly = true)
    public CatalogoProdutosResponse catalogo(UsuarioAutenticado logado, Long fornecedorIdInformado) {
        Long id = logado.isAdmin() ? fornecedorIdInformado : logado.getFornecedorId();
        if (id == null) {
            throw new NegocioException("Selecione o fornecedor no filtro para ver todos os produtos dele.");
        }
        Fornecedor f = fornecedorService.buscar(id);
        List<CatalogoProdutosResponse.Item> itens = new ArrayList<>();
        String aviso = null;

        int qtdWinthor = 0;
        if (f.getCodFornecedorErp() == null) {
            aviso = "Fornecedor sem codigo no WinThor: aparecem apenas os produtos cadastrados pelo portal.";
        } else {
            try {
                for (ErpProdutoConsultaRepository.ProdutoCadastrado p : erp.doFornecedor(f.getCodFornecedorErp())) {
                    itens.add(new CatalogoProdutosResponse.Item("WINTHOR", p.codProd(), null, p.codFab(), p.descricao(),
                            p.ean(), p.ncm(), p.embalagem(), p.unidade(), p.qtUnitCx(), p.pesoLiquido(), p.pesoBruto(),
                            p.dataCadastro(), p.foraDeLinha(), null, null, null));
                    qtdWinthor++;
                }
            } catch (RuntimeException e) {
                log.warn("Falha ao ler os produtos do fornecedor {} no WinThor: {}", f.getCodFornecedorErp(), e.getMessage());
                aviso = "Nao foi possivel carregar os produtos do WinThor agora. Aparecem so os cadastrados pelo portal.";
            }
        }

        // novos do portal ainda sem CODPROD (os ja gravados no WinThor aparecem na lista acima)
        int qtdPortal = 0;
        for (Produto p : novos.semCodigoWinthor(f.getId())) {
            BigDecimal emb = f.getTipo().isPiso() ? p.getM2Caixa()
                    : (p.getQtdEmbalagem() == null ? null : BigDecimal.valueOf(p.getQtdEmbalagem()));
            itens.add(new CatalogoProdutosResponse.Item("PORTAL", null, p.getId(), p.getCodigo(), p.getDescricao(),
                    p.getCodBarraProduto(), p.getNcm(), null, null, emb, p.getPesoLiquido(), p.getPesoBruto(),
                    p.getDataCriacao() == null ? null : p.getDataCriacao().toLocalDate(), false, p.getStatus(),
                    p.getPedido() == null ? null : p.getPedido().getNumero(),
                    p.getPedido() == null ? null : p.getPedido().getId()));
            qtdPortal++;
        }
        return new CatalogoProdutosResponse(itens, qtdWinthor, qtdPortal, aviso);
    }
}
