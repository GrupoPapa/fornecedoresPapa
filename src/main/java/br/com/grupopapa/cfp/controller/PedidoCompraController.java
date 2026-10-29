package br.com.grupopapa.cfp.controller;

import br.com.grupopapa.cfp.domain.PedidoCompra;
import br.com.grupopapa.cfp.domain.StatusPedidoCompra;
import br.com.grupopapa.cfp.dto.*;
import br.com.grupopapa.cfp.repository.ErpCompraRepository;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import br.com.grupopapa.cfp.security.UsuarioLogado;
import br.com.grupopapa.cfp.service.ExportacaoCompraService;
import br.com.grupopapa.cfp.service.PedidoCompraService;
import br.com.grupopapa.cfp.service.SugestaoCompraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pedidos-compra")
@Tag(name = "Pedidos de compra")
public class PedidoCompraController {

    private final PedidoCompraService service;
    private final SugestaoCompraService sugestaoService;
    private final ExportacaoCompraService exportacaoService;

    public PedidoCompraController(PedidoCompraService service,
                                  SugestaoCompraService sugestaoService,
                                  ExportacaoCompraService exportacaoService) {
        this.service = service;
        this.sugestaoService = sugestaoService;
        this.exportacaoService = exportacaoService;
    }

    @GetMapping
    @Operation(summary = "Pedidos de compra do fornecedor logado (admin ve todos)")
    public PaginaResponse<PedidoCompraResponse> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) StatusPedidoCompra status,
            @RequestParam(required = false) Long fornecedorId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        Long escopo = logado.isAdmin() ? fornecedorId : logado.getFornecedorId();
        Page<PedidoCompra> page = service.listar(escopo, status, termo,
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 200),
                        Sort.by(Sort.Direction.DESC, "id")));
        return PaginaResponse.de(page, PedidoCompraResponse::resumo);
    }

    @GetMapping("/catalogo")
    @Operation(summary = "Produtos que o fornecedor pode incluir no pedido (WinThor + novos do portal)")
    public CatalogoCompraResponse catalogo(@RequestParam(required = false) Long fornecedorId) {
        return service.catalogo(UsuarioLogado.obter(), fornecedorId);
    }

    @GetMapping("/filiais")
    @Operation(summary = "Filiais de entrega disponiveis")
    public List<ErpCompraRepository.Filial> filiais() {
        return service.filiais();
    }

    @GetMapping("/configuracao")
    @Operation(summary = "Parametros que a tela precisa conhecer")
    public Map<String, Object> configuracao() {
        return Map.of("gerarSugestaoHabilitada", sugestaoService.isHabilitada());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria o pedido em digitacao")
    public PedidoCompraResponse criar(@RequestBody @Valid PedidoCompraRequest request,
                                      @RequestParam(required = false) Long fornecedorId) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        PedidoCompra criado = service.criar(request, logado, fornecedorId);
        return PedidoCompraResponse.completo(service.buscar(criado.getId(), logado));
    }

    @GetMapping("/{id}")
    public PedidoCompraResponse buscar(@PathVariable Long id) {
        return PedidoCompraResponse.completo(service.buscar(id, UsuarioLogado.obter()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Salva cabecalho e itens (substitui a lista de itens)")
    public PedidoCompraResponse atualizar(@PathVariable Long id, @RequestBody @Valid PedidoCompraRequest request) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        service.atualizar(id, request, logado);
        return PedidoCompraResponse.completo(service.buscar(id, logado));
    }

    @PostMapping("/{id}/enviar")
    @Operation(summary = "Envia para o fiscal, verificando as divergencias de cadastro")
    public PedidoCompraResponse enviar(@PathVariable Long id) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        service.enviar(id, logado);
        return PedidoCompraResponse.completo(service.buscar(id, logado));
    }

    @PostMapping("/{id}/cancelar")
    public PedidoCompraResponse cancelar(@PathVariable Long id,
                                         @RequestBody(required = false) @Valid AnaliseCompraRequest request) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        service.cancelar(id, request == null ? null : request.parecer(), logado);
        return PedidoCompraResponse.completo(service.buscar(id, logado));
    }

    @GetMapping("/{id}/historico")
    public List<HistoricoCompraResponse> historico(@PathVariable Long id) {
        return service.historico(id, UsuarioLogado.obter()).stream().map(HistoricoCompraResponse::de).toList();
    }

    @GetMapping("/{id}/winthor")
    @Operation(summary = "Situacao da sugestao no WinThor (importada na 220? numero do pedido?)")
    public Map<String, Object> situacaoWinthor(@PathVariable Long id) {
        try {
            var s = sugestaoService.situacao(id, UsuarioLogado.obter());
            return Map.of("encontrada", s.encontrada(), "importada", s.importada(), "itens", s.itens());
        } catch (DataAccessException e) {
            return Map.of("erro", "Nao foi possivel consultar o WinThor agora.");
        }
    }

    @GetMapping("/exportar")
    @Operation(summary = "Exporta os pedidos (uma linha por item) em xlsx ou csv")
    public ResponseEntity<byte[]> exportar(@RequestParam(defaultValue = "xlsx") String formato,
                                           @RequestParam(required = false) StatusPedidoCompra status,
                                           @RequestParam(required = false) Long fornecedorId) {
        UsuarioAutenticado logado = UsuarioLogado.obter();
        Long escopo = logado.isAdmin() ? fornecedorId : logado.getFornecedorId();
        List<PedidoCompra> pedidos = service.listarParaExportacao(escopo, status);
        boolean csv = "csv".equalsIgnoreCase(formato);
        byte[] conteudo = csv ? exportacaoService.gerarCsv(pedidos) : exportacaoService.gerarExcel(pedidos);
        MediaType tipo = csv ? MediaType.parseMediaType("text/csv; charset=UTF-8")
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"pedidos-compra." + (csv ? "csv" : "xlsx") + "\"")
                .contentType(tipo)
                .body(conteudo);
    }
}
