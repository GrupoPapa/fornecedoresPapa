package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.TipoAnexoFar;

import java.time.OffsetDateTime;

public record FarAnexoResponse(Long id, TipoAnexoFar tipo, String nomeArquivo, String tipoConteudo,
                               Integer tamanho, OffsetDateTime dataCriacao) {
}
