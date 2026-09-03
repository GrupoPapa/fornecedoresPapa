package br.com.grupopapa.cfp.controller;

import br.com.grupopapa.cfp.dto.DominioResponse;
import br.com.grupopapa.cfp.dto.ErpFornecedorResponse;
import br.com.grupopapa.cfp.dto.FornecedorResponse;
import br.com.grupopapa.cfp.repository.ErpDominioRepository;
import br.com.grupopapa.cfp.service.ErpFornecedorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/erp")
@Tag(name = "Admin - WinThor")
public class AdminErpController {

    private final ErpFornecedorService erpService;
    private final ErpDominioRepository dominioRepository;

    public AdminErpController(ErpFornecedorService erpService,
                              ErpDominioRepository dominioRepository) {
        this.erpService = erpService;
        this.dominioRepository = dominioRepository;
    }

    @GetMapping("/marcas")
    @Operation(summary = "Marcas cadastradas no WinThor")
    public List<DominioResponse> marcas() {
        return dominioRepository.marcas();
    }

    @GetMapping("/departamentos")
    @Operation(summary = "Departamentos do WinThor")
    public List<DominioResponse> departamentos() {
        return dominioRepository.departamentos();
    }

    @GetMapping("/secoes")
    @Operation(summary = "Secoes do WinThor, opcionalmente de um departamento")
    public List<DominioResponse> secoes(@RequestParam(required = false) Integer codEpto) {
        return dominioRepository.secoes(codEpto);
    }

    @GetMapping("/fornecedores")
    @Operation(summary = "Consulta fornecedores na tabela PCFORNEC do WinThor (somente leitura)")
    public List<ErpFornecedorResponse> buscar(@RequestParam(required = false) String termo,
                                              @RequestParam(defaultValue = "50") int limite) {
        return erpService.buscar(termo, limite);
    }

    @PostMapping("/fornecedores/{codFornec}/importar")
    @Operation(summary = "Cria o fornecedor no portal a partir dos dados do WinThor")
    public FornecedorResponse importar(@PathVariable Long codFornec) {
        return FornecedorResponse.de(erpService.importar(codFornec));
    }
}
