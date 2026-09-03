package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.Pedido;
import br.com.grupopapa.cfp.domain.Produto;
import br.com.grupopapa.cfp.domain.ProdutoErp;
import br.com.grupopapa.cfp.domain.StatusPedido;
import br.com.grupopapa.cfp.exception.NegocioException;
import br.com.grupopapa.cfp.exception.RecursoNaoEncontradoException;
import br.com.grupopapa.cfp.repository.ErpProdutoWriter;
import br.com.grupopapa.cfp.repository.ProdutoErpRepository;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Leva os produtos aprovados para o WinThor. */
@Service
public class IntegracaoErpService {

    private static final Logger log = LoggerFactory.getLogger(IntegracaoErpService.class);

    public record ResultadoItem(Long produtoId, String codigo, boolean ok,
                                Long codProdErp, String mensagem) { }

    public record ResultadoIntegracao(int total, int gravados, int comErro,
                                      List<ResultadoItem> itens) { }

    private final ProdutoErpRepository erpRepository;
    private final ErpProdutoWriter writer;
    private final PedidoService pedidoService;
    private final ProdutoService produtoService;
    private final FotoWinthorService fotoWinthorService;
    private final boolean habilitada;
    private final Long matricula;

    public IntegracaoErpService(ProdutoErpRepository erpRepository,
                                ErpProdutoWriter writer,
                                PedidoService pedidoService,
                                ProdutoService produtoService,
                                FotoWinthorService fotoWinthorService,
                                @Value("${app.erp.integracao.habilitada:false}") boolean habilitada,
                                @Value("${app.erp.matricula:}") String matricula) {
        this.erpRepository = erpRepository;
        this.writer = writer;
        this.pedidoService = pedidoService;
        this.produtoService = produtoService;
        this.fotoWinthorService = fotoWinthorService;
        this.habilitada = habilitada;
        this.matricula = converterMatricula(matricula);
    }

    /** Valor invalido nao pode derrubar a aplicacao: vira "nao configurada". */
    private static Long converterMatricula(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            log.warn("CFP_ERP_MATRICULA invalida ({}) - a gravacao no WinThor ficara bloqueada.", valor);
            return null;
        }
    }

    public boolean isHabilitada() {
        return habilitada;
    }

    @Transactional
    public ResultadoIntegracao integrarPedido(Long pedidoId, UsuarioAutenticado logado) {
        Pedido pedido = pedidoService.buscar(pedidoId, logado);
        if (pedido.getStatus() != StatusPedido.APROVADO) {
            throw new NegocioException("Somente pedidos aprovados podem ser gravados no WinThor.");
        }
        validarConfiguracao();

        List<ResultadoItem> itens = new ArrayList<>();
        for (Produto produto : pedidoService.itens(pedidoId)) {
            itens.add(integrarItem(produto));
        }
        int gravados = (int) itens.stream().filter(ResultadoItem::ok).count();
        return new ResultadoIntegracao(itens.size(), gravados, itens.size() - gravados, itens);
    }

    @Transactional
    public ResultadoItem integrarProduto(Long produtoId, UsuarioAutenticado logado) {
        validarConfiguracao();
        return integrarItem(produtoService.buscar(produtoId, logado));
    }

    // ------------------------------------------------------------------ apoio

    private void validarConfiguracao() {
        if (!habilitada) {
            throw new NegocioException("A gravacao no WinThor esta desligada. "
                    + "Ligue em CFP_ERP_INTEGRACAO para habilitar.");
        }
        if (matricula == null) {
            throw new NegocioException("Informe a matricula do WinThor em CFP_ERP_MATRICULA "
                    + "antes de gravar - ela carimba quem cadastrou o produto no ERP.");
        }
    }

    private ResultadoItem integrarItem(Produto produto) {
        ProdutoErp erp = erpRepository.findById(produto.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto " + produto.getCodigo() + " esta sem os dados do WinThor."));

        if ("INTEGRADO".equals(erp.getStatusIntegracao())) {
            // ja esta no ERP: aproveita para reenviar a foto, caso a copia tenha falhado antes
            var fotoRefeita = fotoWinthorService.copiar(produto.getId(), erp.getCodProdErp());
            return new ResultadoItem(produto.getId(), produto.getCodigo(), true,
                    erp.getCodProdErp(), "Ja estava gravado." + textoFoto(fotoRefeita));
        }
        if (!erp.isCompleto()) {
            return registrarErro(produto, erp, "Dados do WinThor incompletos.");
        }
        if (produto.getFornecedor().getCodFornecedorErp() == null) {
            return registrarErro(produto, erp,
                    "O fornecedor nao tem codigo do WinThor. Importe-o novamente na tela de fornecedores.");
        }

        try {
            Long codProd = writer.gravar(erp, produto.getFornecedor(), matricula);
            erp.setStatusIntegracao("INTEGRADO");
            erp.setCodProdErp(codProd);
            erp.setDataIntegracao(OffsetDateTime.now());
            erp.setMensagemErro(null);
            erpRepository.save(erp);
            log.info("Produto {} gravado no WinThor com o codigo {}", produto.getCodigo(), codProd);
            // leva as fotos para a pasta do WinThor com o CODPROD como nome do arquivo
            var foto = fotoWinthorService.copiar(produto.getId(), codProd);
            return new ResultadoItem(produto.getId(), produto.getCodigo(), true, codProd,
                    "Gravado com o codigo " + codProd + "." + textoFoto(foto));
        } catch (Exception e) {
            String causa = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            log.error("Falha ao gravar {} no WinThor: {}", produto.getCodigo(), causa);
            return registrarErro(produto, erp, causa);
        }
    }

    /** Complemento da mensagem sobre a copia das fotos para a pasta do WinThor. */
    private String textoFoto(FotoWinthorService.Resultado foto) {
        if (!fotoWinthorService.isConfigurado()) {
            return "";
        }
        if (!foto.ok()) {
            return " Atencao: a foto nao foi copiada para a pasta do WinThor (" + foto.erro() + ").";
        }
        if (foto.copiadas() == 0) {
            return " Sem foto para copiar.";
        }
        return " " + foto.copiadas() + " foto(s) copiada(s) para a pasta do WinThor.";
    }

    private ResultadoItem registrarErro(Produto produto, ProdutoErp erp, String mensagem) {
        erp.setStatusIntegracao("ERRO");
        erp.setMensagemErro(mensagem == null ? "Erro desconhecido" : cortar(mensagem, 1000));
        erpRepository.save(erp);
        return new ResultadoItem(produto.getId(), produto.getCodigo(), false, null, erp.getMensagemErro());
    }

    private String cortar(String v, int max) {
        return v.length() <= max ? v : v.substring(0, max);
    }
}
