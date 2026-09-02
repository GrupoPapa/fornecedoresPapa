package br.com.grupopapa.cfp.dto;

import java.time.OffsetDateTime;

/** Metadados da foto - o conteudo e baixado por endpoint proprio. */
public record FotoResponse(Long id, String nomeArquivo, String tipoConteudo,
                           Integer tamanho, Boolean principal, OffsetDateTime dataCriacao) {
}
