package br.com.grupopapa.cfp.controller;

import br.com.grupopapa.cfp.dto.FornecedorRequest;
import br.com.grupopapa.cfp.dto.FornecedorResponse;
import br.com.grupopapa.cfp.dto.PaginaResponse;
import br.com.grupopapa.cfp.service.FornecedorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/fornecedores")
@Tag(name = "Admin - Fornecedores")
public class AdminFornecedorController {

    private final FornecedorService fornecedorService;

    public AdminFornecedorController(FornecedorService fornecedorService) {
        this.fornecedorService = fornecedorService;
    }

    @GetMapping
    public PaginaResponse<FornecedorResponse> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return PaginaResponse.de(
                fornecedorService.listar(termo, PageRequest.of(pagina, Math.min(tamanho, 200))),
                FornecedorResponse::de);
    }

    @GetMapping("/todos")
    public List<FornecedorResponse> todos() {
        return fornecedorService.listarTodos().stream().map(FornecedorResponse::de).toList();
    }

    @GetMapping("/{id}")
    public FornecedorResponse buscar(@PathVariable Long id) {
        return FornecedorResponse.de(fornecedorService.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FornecedorResponse criar(@RequestBody @Valid FornecedorRequest request) {
        return FornecedorResponse.de(fornecedorService.criar(request));
    }

    @PutMapping("/{id}")
    public FornecedorResponse atualizar(@PathVariable Long id, @RequestBody @Valid FornecedorRequest request) {
        return FornecedorResponse.de(fornecedorService.atualizar(id, request));
    }
}
