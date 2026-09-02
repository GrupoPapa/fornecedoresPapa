package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.Fornecedor;
import br.com.grupopapa.cfp.domain.TipoFornecedor;
import br.com.grupopapa.cfp.dto.FornecedorRequest;
import br.com.grupopapa.cfp.exception.NegocioException;
import br.com.grupopapa.cfp.exception.RecursoNaoEncontradoException;
import br.com.grupopapa.cfp.repository.FornecedorRepository;
import br.com.grupopapa.cfp.validation.CnpjValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FornecedorService {

    private final FornecedorRepository repository;

    public FornecedorService(FornecedorRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<Fornecedor> listar(String termo, Pageable pageable) {
        return repository.buscar(normalizarTermo(termo), pageable);
    }

    @Transactional(readOnly = true)
    public List<Fornecedor> listarTodos() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Fornecedor buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor nao encontrado."));
    }

    @Transactional
    public Fornecedor criar(FornecedorRequest request) {
        String cnpj = CnpjValidator.somenteDigitos(request.cnpj());
        if (repository.existsByCnpj(cnpj)) {
            throw new NegocioException("Ja existe um fornecedor cadastrado com este CNPJ.");
        }
        Fornecedor f = new Fornecedor();
        f.setCnpj(cnpj);
        aplicar(f, request);
        return repository.save(f);
    }

    @Transactional
    public Fornecedor atualizar(Long id, FornecedorRequest request) {
        Fornecedor f = buscar(id);
        String cnpj = CnpjValidator.somenteDigitos(request.cnpj());
        if (!f.getCnpj().equals(cnpj) && repository.existsByCnpj(cnpj)) {
            throw new NegocioException("Ja existe um fornecedor cadastrado com este CNPJ.");
        }
        f.setCnpj(cnpj);
        aplicar(f, request);
        return repository.save(f);
    }

    private void aplicar(Fornecedor f, FornecedorRequest r) {
        f.setRazaoSocial(r.razaoSocial().trim());
        f.setNomeFantasia(r.nomeFantasia() != null ? r.nomeFantasia().trim() : null);
        f.setEmail(r.email() != null ? r.email().trim().toLowerCase() : null);
        f.setTelefone(r.telefone());
        f.setCodFornecedorErp(r.codFornecedorErp());
        f.setTipo(r.tipo() == null ? TipoFornecedor.GERAL : r.tipo());
        f.setAtivo(r.ativo() == null || r.ativo());
    }

    private String normalizarTermo(String termo) {
        return (termo == null || termo.isBlank()) ? null : termo.trim();
    }
}
