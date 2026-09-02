package br.com.grupopapa.cfp.dto;

public record ResumoResponse(long rascunho, long enviado, long aprovado, long reprovado) {
    public long total() { return rascunho + enviado + aprovado + reprovado; }
}
