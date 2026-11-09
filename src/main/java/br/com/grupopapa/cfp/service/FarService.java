package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.*;
import br.com.grupopapa.cfp.dto.*;
import br.com.grupopapa.cfp.exception.NegocioException;
import br.com.grupopapa.cfp.exception.RecursoNaoEncontradoException;
import br.com.grupopapa.cfp.repository.*;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * FAR - controle de avarias. O modulo so existe para os fornecedores liberados
 * (CFP_FORNECEDOR.HABILITA_FAR); a equipe interna enxerga todos e e quem registra
 * o vinculo com a baixa fiscal da avaria no sistema.
 */
@Service
public class FarService {

    private static final Set<String> TIPOS_ACEITOS =
            Set.of("application/pdf", "image/jpeg", "image/jpg", "image/png", "image/webp");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FarRepository repository;
    private final FarAnexoRepository anexoRepository;
    private final FarHistoricoRepository historicoRepository;
    private final FarMotivoRepository motivoRepository;
    private final FornecedorService fornecedorService;
    private final UsuarioRepository usuarioRepository;
    private final long maxBytes;
    private final int maxAnexos;

    public FarService(FarRepository repository,
                      FarAnexoRepository anexoRepository,
                      FarHistoricoRepository historicoRepository,
                      FarMotivoRepository motivoRepository,
                      FornecedorService fornecedorService,
                      UsuarioRepository usuarioRepository,
                      @Value("${app.far.anexo-max-mb:5}") long maxMb,
                      @Value("${app.far.max-anexos:10}") int maxAnexos) {
        this.repository = repository;
        this.anexoRepository = anexoRepository;
        this.historicoRepository = historicoRepository;
        this.motivoRepository = motivoRepository;
        this.fornecedorService = fornecedorService;
        this.usuarioRepository = usuarioRepository;
        this.maxBytes = maxMb * 1024L * 1024L;
        this.maxAnexos = maxAnexos;
    }

    // ================================================================ acesso

    /** Fornecedor sem o modulo liberado nao acessa nenhuma rota de FAR. */
    public void verificarAcesso(UsuarioAutenticado logado) {
        if (logado.isAdmin()) {
            return;
        }
        Fornecedor f = logado.getUsuario().getFornecedor();
        if (f == null || !f.isFarHabilitado()) {
            throw new AccessDeniedException("Controle de avarias nao liberado para este fornecedor.");
        }
    }

    private Fornecedor fornecedorDoEscopo(UsuarioAutenticado logado, Long fornecedorIdInformado) {
        verificarAcesso(logado);
        if (!logado.isAdmin()) {
            return fornecedorService.buscar(logado.getFornecedorId());
        }
        if (fornecedorIdInformado == null) {
            throw new NegocioException("Selecione o fornecedor da FAR.");
        }
        Fornecedor f = fornecedorService.buscar(fornecedorIdInformado);
        if (!f.isFarHabilitado()) {
            throw new NegocioException("O controle de avarias nao esta liberado para " + f.getRazaoSocial()
                    + ". Libere em Fornecedores > Editar.");
        }
        return f;
    }

    // ================================================================ consulta

    @Transactional(readOnly = true)
    public Page<Far> listar(UsuarioAutenticado logado, Long fornecedorId, StatusFar status, Long motivoId,
                            Boolean baixaFiscal, LocalDate dataDe, LocalDate dataAte, String termo,
                            Pageable pageable) {
        verificarAcesso(logado);
        Long escopo = logado.isAdmin() ? fornecedorId : logado.getFornecedorId();
        return repository.pesquisar(escopo, status, motivoId, baixaFiscal, dataDe, dataAte,
                (termo == null || termo.isBlank()) ? null : termo.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public List<Far> listarParaExportacao(UsuarioAutenticado logado, Long fornecedorId, StatusFar status,
                                          Long motivoId, Boolean baixaFiscal, LocalDate dataDe, LocalDate dataAte) {
        verificarAcesso(logado);
        Long escopo = logado.isAdmin() ? fornecedorId : logado.getFornecedorId();
        return repository.listarParaExportacao(escopo, status, motivoId, baixaFiscal, dataDe, dataAte);
    }

    @Transactional(readOnly = true)
    public Far buscar(Long id, UsuarioAutenticado logado) {
        verificarAcesso(logado);
        Far far = repository.buscarCompleto(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("FAR nao encontrada."));
        if (!logado.isAdmin() && !Objects.equals(far.getFornecedor().getId(), logado.getFornecedorId())) {
            throw new RecursoNaoEncontradoException("FAR nao encontrada.");
        }
        return far;
    }

    /** Converte para a resposta, ja com a contagem de comprovantes. */
    @Transactional(readOnly = true)
    public FarResponse resposta(Far far, UsuarioAutenticado logado) {
        long comprovantes = anexoRepository.countByFarIdAndTipo(far.getId(), TipoAnexoFar.COMPROVANTE_PAGAMENTO);
        return FarResponse.de(far, comprovantes, podeEditar(far, logado), true);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<FarResponse> respostaPagina(Page<Far> page, UsuarioAutenticado logado) {
        Map<Long, Long> comprovantes = comprovantesPorFar(page.getContent().stream().map(Far::getId).toList());
        return PaginaResponse.de(page,
                f -> FarResponse.de(f, comprovantes.getOrDefault(f.getId(), 0L), podeEditar(f, logado), false));
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> comprovantesPorFar(Collection<Long> ids) {
        Map<Long, Long> mapa = new HashMap<>();
        if (ids.isEmpty()) {
            return mapa;
        }
        for (Object[] linha : anexoRepository.contarPorTipo(ids, TipoAnexoFar.COMPROVANTE_PAGAMENTO)) {
            mapa.put((Long) linha[0], ((Number) linha[1]).longValue());
        }
        return mapa;
    }

    @Transactional(readOnly = true)
    public List<FarHistorico> historico(Long id, UsuarioAutenticado logado) {
        buscar(id, logado);
        return historicoRepository.listar(id);
    }

    private boolean podeEditar(Far far, UsuarioAutenticado logado) {
        return logado.isAdmin() || far.isEditavelPeloFornecedor();
    }

    // ================================================================ cadastro

    @Transactional
    public Far criar(FarRequest request, MultipartFile comprovante, UsuarioAutenticado logado,
                     Long fornecedorIdInformado) {
        Fornecedor fornecedor = fornecedorDoEscopo(logado, fornecedorIdInformado);
        if (!Boolean.TRUE.equals(fornecedor.getAtivo())) {
            throw new NegocioException("Fornecedor inativo.");
        }
        Usuario usuario = usuarioRepository.getReferenceById(logado.getId());
        boolean temComprovante = arquivoInformado(comprovante);
        if (temComprovante) {
            validarArquivo(comprovante);
        }

        Far far = new Far();
        far.setFornecedor(fornecedor);
        far.setUsuarioCriacao(usuario);
        aplicar(far, request, logado, true);
        validar(far, temComprovante ? 1 : 0, null);

        far = repository.saveAndFlush(far);
        registrar(far.getId(), usuario, "CRIACAO", "FAR registrada: " + resumo(far) + ".");
        if (temComprovante) {
            salvarAnexo(far, comprovante, TipoAnexoFar.COMPROVANTE_PAGAMENTO, usuario);
        }
        return far;
    }

    @Transactional
    public Far atualizar(Long id, FarRequest request, MultipartFile comprovante, UsuarioAutenticado logado) {
        Far far = buscar(id, logado);
        exigirEdicao(far, logado);
        Usuario usuario = usuarioRepository.getReferenceById(logado.getId());
        boolean temComprovante = arquivoInformado(comprovante);
        if (temComprovante) {
            validarArquivo(comprovante);
            validarLimiteAnexos(far.getId());
        }

        Map<String, String> antes = retrato(far);
        aplicar(far, request, logado, false);
        long comprovantes = anexoRepository.countByFarIdAndTipo(far.getId(), TipoAnexoFar.COMPROVANTE_PAGAMENTO)
                + (temComprovante ? 1 : 0);
        validar(far, comprovantes, far.getId());

        far = repository.saveAndFlush(far);
        String mudancas = diferencas(antes, retrato(far));
        if (!mudancas.isEmpty()) {
            boolean mudouStatus = !Objects.equals(antes.get("Situacao"), retrato(far).get("Situacao"));
            registrar(far.getId(), usuario, mudouStatus ? "STATUS" : "ALTERACAO", mudancas);
        }
        if (temComprovante) {
            salvarAnexo(far, comprovante, TipoAnexoFar.COMPROVANTE_PAGAMENTO, usuario);
        }
        return far;
    }

    private void aplicar(Far far, FarRequest r, UsuarioAutenticado logado, boolean novo) {
        far.setDataVisita(r.dataVisita());
        far.setQtdProdutos(r.qtdProdutos());

        boolean motivoMudou = novo || far.getMotivo() == null || !Objects.equals(far.getMotivo().getId(), r.motivoId());
        if (motivoMudou) {
            FarMotivo motivo = motivoRepository.findById(r.motivoId())
                    .orElseThrow(() -> new NegocioException("Motivo nao encontrado."));
            if (!Boolean.TRUE.equals(motivo.getAtivo())) {
                throw new NegocioException("O motivo \"" + motivo.getDescricao() + "\" nao esta mais disponivel.");
            }
            far.setMotivo(motivo);
        }

        far.setObsOcorrencia(texto(r.obsOcorrencia()));
        far.setDataAberturaFar(r.dataAberturaFar());
        far.setNumeroFar(texto(r.numeroFar()));
        far.setStatus(r.status());
        far.setStatusOutros(r.status() == StatusFar.OUTROS ? texto(r.statusOutros()) : null);
        far.setObsFarFornecedor(texto(r.obsFarFornecedor()));
        if (logado.isAdmin()) {
            far.setObsFarEmpresa(texto(r.obsFarEmpresa())); // observacao da empresa: so a equipe interna
        }
    }

    private void validar(Far far, long comprovantes, Long ignorarId) {
        LocalDate hoje = LocalDate.now();
        if (far.getDataVisita().isAfter(hoje)) {
            throw new NegocioException("A data da visita nao pode ser futura.");
        }
        if (far.getDataAberturaFar() != null) {
            if (far.getDataAberturaFar().isAfter(hoje)) {
                throw new NegocioException("A data de abertura da FAR nao pode ser futura.");
            }
            if (far.getDataAberturaFar().isBefore(far.getDataVisita())) {
                throw new NegocioException("A data de abertura da FAR nao pode ser anterior a data da visita.");
            }
        }
        boolean temNumero = far.getNumeroFar() != null;
        boolean temAbertura = far.getDataAberturaFar() != null;
        if (temNumero && !temAbertura) {
            throw new NegocioException("Informe a data de abertura da FAR " + far.getNumeroFar() + ".");
        }
        if (temAbertura && !temNumero) {
            throw new NegocioException("Informe o numero da FAR aberta em " + far.getDataAberturaFar().format(DATA) + ".");
        }
        if (far.getStatus().isFinalizado() && !temNumero) {
            throw new NegocioException("Para finalizar o processo informe o numero e a data de abertura da FAR.");
        }
        if (far.getStatus() == StatusFar.OUTROS && far.getStatusOutros() == null) {
            throw new NegocioException("Descreva a situacao quando escolher \"Outros\".");
        }
        if (far.getStatus() == StatusFar.FINALIZADO_APROVADO && comprovantes == 0) {
            throw new NegocioException("Processo finalizado aprovado exige o comprovante de pagamento anexado.");
        }
        if (temNumero && repository.existeNumero(far.getFornecedor().getId(), far.getNumeroFar(), ignorarId)) {
            throw new NegocioException("Ja existe uma FAR com o numero " + far.getNumeroFar() + " para este fornecedor.");
        }
    }

    private void exigirEdicao(Far far, UsuarioAutenticado logado) {
        if (!podeEditar(far, logado)) {
            throw new NegocioException("Esta FAR ja foi vinculada a baixa fiscal e nao pode mais ser alterada. "
                    + "Fale com a equipe interna se precisar corrigir algo.");
        }
    }

    // ================================================================ anexos

    @Transactional(readOnly = true)
    public List<FarAnexoResponse> listarAnexos(Long farId, UsuarioAutenticado logado) {
        buscar(farId, logado);
        return anexoRepository.listar(farId);
    }

    @Transactional
    public FarAnexoResponse anexar(Long farId, MultipartFile arquivo, TipoAnexoFar tipo, UsuarioAutenticado logado) {
        Far far = buscar(farId, logado);
        exigirEdicao(far, logado);
        if (!arquivoInformado(arquivo)) {
            throw new NegocioException("Selecione o arquivo.");
        }
        validarArquivo(arquivo);
        validarLimiteAnexos(farId);
        FarAnexo a = salvarAnexo(far, arquivo, tipo == null ? TipoAnexoFar.OUTRO : tipo,
                usuarioRepository.getReferenceById(logado.getId()));
        return new FarAnexoResponse(a.getId(), a.getTipo(), a.getNomeArquivo(), a.getTipoConteudo(),
                a.getTamanho(), a.getDataCriacao());
    }

    @Transactional(readOnly = true)
    public FarAnexo baixarAnexo(Long farId, Long anexoId, UsuarioAutenticado logado) {
        buscar(farId, logado);
        FarAnexo anexo = anexoRepository.findByIdAndFarId(anexoId, farId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo nao encontrado."));
        anexo.getConteudo().getClass(); // carrega o BLOB dentro da transacao
        return anexo;
    }

    @Transactional
    public void excluirAnexo(Long farId, Long anexoId, UsuarioAutenticado logado) {
        Far far = buscar(farId, logado);
        exigirEdicao(far, logado);
        FarAnexo anexo = anexoRepository.findByIdAndFarId(anexoId, farId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo nao encontrado."));
        if (anexo.getTipo() == TipoAnexoFar.COMPROVANTE_PAGAMENTO
                && far.getStatus() == StatusFar.FINALIZADO_APROVADO
                && anexoRepository.countByFarIdAndTipo(farId, TipoAnexoFar.COMPROVANTE_PAGAMENTO) <= 1) {
            throw new NegocioException("Este e o unico comprovante de pagamento de um processo aprovado. "
                    + "Anexe o novo comprovante antes de excluir este.");
        }
        anexoRepository.delete(anexo);
        registrar(farId, usuarioRepository.getReferenceById(logado.getId()), "ANEXO_EXCLUIDO",
                rotuloAnexo(anexo.getTipo()) + " excluido: " + anexo.getNomeArquivo());
    }

    private FarAnexo salvarAnexo(Far far, MultipartFile arquivo, TipoAnexoFar tipo, Usuario usuario) {
        FarAnexo a = new FarAnexo();
        a.setFarId(far.getId());
        a.setTipo(tipo);
        a.setNomeArquivo(nomeSeguro(arquivo.getOriginalFilename()));
        a.setTipoConteudo(arquivo.getContentType().toLowerCase());
        a.setTamanho((int) arquivo.getSize());
        a.setUsuario(usuario);
        try {
            a.setConteudo(arquivo.getBytes());
        } catch (IOException e) {
            throw new NegocioException("Nao foi possivel ler o arquivo enviado.");
        }
        a = anexoRepository.save(a);
        registrar(far.getId(), usuario, "ANEXO", rotuloAnexo(tipo) + " anexado: " + a.getNomeArquivo());
        return a;
    }

    private boolean arquivoInformado(MultipartFile arquivo) {
        return arquivo != null && !arquivo.isEmpty();
    }

    private void validarArquivo(MultipartFile arquivo) {
        if (arquivo.getSize() > maxBytes) {
            throw new NegocioException("O arquivo tem " + (arquivo.getSize() / 1024 / 1024)
                    + " MB. O limite e de " + (maxBytes / 1024 / 1024) + " MB.");
        }
        String tipo = arquivo.getContentType() == null ? "" : arquivo.getContentType().toLowerCase();
        if (!TIPOS_ACEITOS.contains(tipo)) {
            throw new NegocioException("Formato nao aceito. Envie PDF, JPG, PNG ou WEBP.");
        }
    }

    private void validarLimiteAnexos(Long farId) {
        if (anexoRepository.countByFarId(farId) >= maxAnexos) {
            throw new NegocioException("Esta FAR ja tem " + maxAnexos + " anexos. Exclua um antes de anexar outro.");
        }
    }

    private String rotuloAnexo(TipoAnexoFar tipo) {
        return tipo == TipoAnexoFar.COMPROVANTE_PAGAMENTO ? "Comprovante de pagamento" : "Anexo";
    }

    // ================================================================ equipe interna

    @Transactional
    public Far vincularBaixaFiscal(Long id, BaixaFiscalRequest request, UsuarioAutenticado logado) {
        Far far = buscar(id, logado);
        if (Boolean.TRUE.equals(far.getBaixaFiscal())) {
            throw new NegocioException("Esta FAR ja esta vinculada a baixa fiscal.");
        }
        if (far.getNumeroFar() == null) {
            throw new NegocioException("A FAR ainda nao tem numero. Ela precisa estar aberta para ser vinculada.");
        }
        Usuario usuario = usuarioRepository.getReferenceById(logado.getId());
        far.setBaixaFiscal(true);
        far.setBaixaFiscalReferencia(request == null ? null : texto(request.referencia()));
        far.setBaixaFiscalObservacao(request == null ? null : texto(request.observacao()));
        far.setDataBaixaFiscal(OffsetDateTime.now());
        far.setUsuarioBaixa(usuario);
        far = repository.save(far);
        StringBuilder sb = new StringBuilder("Vinculo com a baixa fiscal registrado");
        if (far.getBaixaFiscalReferencia() != null) sb.append(". Referencia: ").append(far.getBaixaFiscalReferencia());
        if (far.getBaixaFiscalObservacao() != null) sb.append(". ").append(far.getBaixaFiscalObservacao());
        registrar(far.getId(), usuario, "BAIXA_FISCAL", sb.append('.').toString());
        return far;
    }

    @Transactional
    public Far desfazerBaixaFiscal(Long id, String motivo, UsuarioAutenticado logado) {
        Far far = buscar(id, logado);
        if (!Boolean.TRUE.equals(far.getBaixaFiscal())) {
            throw new NegocioException("Esta FAR nao esta vinculada a baixa fiscal.");
        }
        String anterior = far.getBaixaFiscalReferencia();
        far.setBaixaFiscal(false);
        far.setBaixaFiscalReferencia(null);
        far.setBaixaFiscalObservacao(null);
        far.setDataBaixaFiscal(null);
        far.setUsuarioBaixa(null);
        far = repository.save(far);
        registrar(far.getId(), usuarioRepository.getReferenceById(logado.getId()), "BAIXA_DESFEITA",
                "Vinculo com a baixa fiscal desfeito" + (anterior != null ? " (referencia " + anterior + ")" : "")
                        + (texto(motivo) != null ? ". Motivo: " + motivo.trim() : "") + ".");
        return far;
    }

    // ================================================================ motivos

    @Transactional(readOnly = true)
    public List<FarMotivo> motivosAtivos(UsuarioAutenticado logado) {
        verificarAcesso(logado);
        return motivoRepository.findByAtivoTrueOrderByDescricao();
    }

    @Transactional(readOnly = true)
    public List<FarMotivo> todosMotivos() {
        return motivoRepository.findAllByOrderByDescricao();
    }

    @Transactional
    public FarMotivo salvarMotivo(Long id, FarMotivoRequest request) {
        String descricao = request.descricao().trim();
        if (motivoRepository.existeDescricao(descricao, id)) {
            throw new NegocioException("Ja existe o motivo \"" + descricao + "\".");
        }
        FarMotivo m = id == null ? new FarMotivo()
                : motivoRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Motivo nao encontrado."));
        m.setDescricao(descricao);
        m.setAtivo(request.ativo() == null || request.ativo());
        return motivoRepository.save(m);
    }

    // ================================================================ apoio

    private void registrar(Long farId, Usuario usuario, String evento, String descricao) {
        historicoRepository.save(FarHistorico.de(farId, usuario, evento, descricao));
    }

    private String resumo(Far f) {
        StringBuilder sb = new StringBuilder();
        sb.append("visita em ").append(f.getDataVisita().format(DATA))
          .append(", ").append(f.getQtdProdutos()).append(" produto(s), motivo ").append(f.getMotivo().getDescricao())
          .append(", situacao ").append(rotuloStatus(f));
        if (f.getNumeroFar() != null) sb.append(", FAR ").append(f.getNumeroFar());
        return sb.toString();
    }

    private String rotuloStatus(Far f) {
        return f.getStatus() == StatusFar.OUTROS && f.getStatusOutros() != null
                ? "Outros (" + f.getStatusOutros() + ")" : f.getStatus().getRotulo();
    }

    /** Campos acompanhados no historico, com o rotulo que aparece na tela. */
    private Map<String, String> retrato(Far f) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Data da visita", f.getDataVisita() == null ? null : f.getDataVisita().format(DATA));
        m.put("Qtd. produtos", f.getQtdProdutos() == null ? null : f.getQtdProdutos().toString());
        m.put("Motivo", f.getMotivo() == null ? null : f.getMotivo().getDescricao());
        m.put("Obs. ocorrencia", f.getObsOcorrencia());
        m.put("Data abertura FAR", f.getDataAberturaFar() == null ? null : f.getDataAberturaFar().format(DATA));
        m.put("N. FAR", f.getNumeroFar());
        m.put("Situacao", f.getStatus() == null ? null : rotuloStatus(f));
        m.put("Obs. FAR fornecedor", f.getObsFarFornecedor());
        m.put("Obs. FAR empresa", f.getObsFarEmpresa());
        return m;
    }

    private String diferencas(Map<String, String> antes, Map<String, String> depois) {
        List<String> linhas = new ArrayList<>();
        for (Map.Entry<String, String> e : depois.entrySet()) {
            String de = antes.get(e.getKey());
            String para = e.getValue();
            if (!Objects.equals(de, para)) {
                linhas.add(e.getKey() + ": " + (de == null ? "(vazio)" : curto(de))
                        + " -> " + (para == null ? "(vazio)" : curto(para)));
            }
        }
        return String.join("\n", linhas);
    }

    private String curto(String v) {
        return v.length() > 150 ? v.substring(0, 150) + "..." : v;
    }

    private String texto(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private String nomeSeguro(String nome) {
        if (nome == null || nome.isBlank()) {
            return "anexo";
        }
        String limpo = nome.replaceAll("[\\\\/\\r\\n\"]", "_").trim();
        return limpo.length() > 200 ? limpo.substring(limpo.length() - 200) : limpo;
    }
}
