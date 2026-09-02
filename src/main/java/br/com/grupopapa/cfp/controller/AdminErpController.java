package br.com.grupopapa.cfp.controller;

import br.com.grupopapa.cfp.dto.ErpFornecedorResponse;
import br.com.grupopapa.cfp.dto.FornecedorResponse;
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

    public AdminErpController(ErpFornecedorService erpService) {
        this.erpService = erpService;
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
