package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.Fornecedor;
import br.com.grupopapa.cfp.domain.TipoFornecedor;
import br.com.grupopapa.cfp.dto.ErpFornecedorResponse;
import br.com.grupopapa.cfp.exception.NegocioException;
import br.com.grupopapa.cfp.exception.RecursoNaoEncontradoException;
import br.com.grupopapa.cfp.repository.ErpFornecedorRepository;
import br.com.grupopapa.cfp.repository.FornecedorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ErpFornecedorService {

    private final ErpFornecedorRepository erpRepository;
    private final FornecedorRepository fornecedorRepository;
    private final String tabela;

    public ErpFornecedorService(ErpFornecedorRepository erpRepository,
                                FornecedorRepository fornecedorRepository,
                                @Value("${app.erp.tabela-fornecedor:PCFORNEC}") String tabela) {
        this.erpRepository = erpRepository;
        this.fornecedorRepository = fornecedorRepository;
        this.tabela = tabela;
    }

    /** Traduz falhas de banco em uma mensagem que ajuda a corrigir a configuracao. */
    private NegocioException erroConsultaErp(DataAccessException e) {
        String causa = e.getMostSpecificCause().getMessage();
        return new NegocioException("Nao foi possivel consultar o WinThor: "
                + (causa == null ? e.getMessage() : causa.trim())
                + " | Sao lidas as tabelas de fornecedor (" + tabela + ") e de produto."
                + " Verifique se o usuario do portal enxerga as duas e, se precisarem de schema,"
                + " ajuste app.erp.tabela-fornecedor e app.erp.tabela-produto (ex.: DBA." + tabela + ").");
    }

    /** Lista fornecedores do WinThor, marcando os que ja foram importados. */
    @Transactional(readOnly = true)
    public List<ErpFornecedorResponse> buscar(String termo, int limite) {
        List<ErpFornecedorRepository.ErpFornecedor> encontrados;
        Set<Long> piso;
        try {
            encontrados = erpRepository.buscar(termo, Math.min(Math.max(limite, 1), 200));
            piso = erpRepository.codigosDePisoRevestimento(
                    encontrados.stream().map(ErpFornecedorRepository.ErpFornecedor::codigo).toList());
        } catch (DataAccessException e) {
            throw erroConsultaErp(e);
        }
        return encontrados.stream()
                .map(e -> {
                    String cnpj = somenteDigitos(e.cgc());
                    Optional<Fornecedor> local = cnpj.isEmpty()
                            ? Optional.empty()
                            : fornecedorRepository.findByCnpj(cnpj);
                    TipoFornecedor tipo = piso.contains(e.codigo())
                            ? TipoFornecedor.PISO_REVESTIMENTO : TipoFornecedor.GERAL;
                    return new ErpFornecedorResponse(
                            e.codigo(), cnpj, e.razaoSocial(),
                            limpar(e.telefone()), limpar(e.email()), tipo,
                            local.isPresent(),
                            local.map(Fornecedor::getId).orElse(null));
                })
                .toList();
    }

    /**
     * Cria (ou atualiza) o fornecedor local a partir do registro do ERP.
     * Se o CNPJ ja existir, apenas amarra o codigo do ERP e atualiza os dados de contato.
     */
    @Transactional
    public Fornecedor importar(Long codFornecErp) {
        ErpFornecedorRepository.ErpFornecedor erp;
        try {
            erp = erpRepository.buscarPorCodigo(codFornecErp)
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "Fornecedor " + codFornecErp + " nao encontrado no WinThor."));
        } catch (DataAccessException e) {
            throw erroConsultaErp(e);
        }

        String cnpj = somenteDigitos(erp.cgc());
        if (cnpj.isEmpty()) {
            throw new NegocioException("O fornecedor " + codFornecErp
                    + " esta sem CNPJ/CPF preenchido no WinThor. Ajuste no ERP e tente novamente.");
        }
        if (erp.razaoSocial() == null || erp.razaoSocial().isBlank()) {
            throw new NegocioException("O fornecedor " + codFornecErp + " esta sem razao social no WinThor.");
        }

        boolean piso;
        try {
            piso = erpRepository.ehPisoRevestimento(erp.codigo());
        } catch (DataAccessException e) {
            throw erroConsultaErp(e);
        }

        Fornecedor f = fornecedorRepository.findByCnpj(cnpj).orElseGet(Fornecedor::new);
        f.setCnpj(cnpj);
        f.setTipo(piso ? TipoFornecedor.PISO_REVESTIMENTO : TipoFornecedor.GERAL);
        f.setRazaoSocial(cortar(erp.razaoSocial(), 150));
        f.setTelefone(cortar(limpar(erp.telefone()), 20));
        f.setEmail(cortar(limpar(erp.email()), 150));
        f.setCodFornecedorErp(erp.codigo());
        if (f.getAtivo() == null) {
            f.setAtivo(Boolean.TRUE);
        }
        return fornecedorRepository.save(f);
    }

    private String somenteDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    private String limpar(String valor) {
        if (valor == null) return null;
        String t = valor.trim();
        return t.isEmpty() ? null : t;
    }

    private String cortar(String valor, int max) {
        if (valor == null) return null;
        return valor.length() <= max ? valor : valor.substring(0, max);
    }
}
