package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.ProdutoHistorico;

import java.time.OffsetDateTime;

public record HistoricoResponse(
        Long id, String acao, String campo, String valorAnterior, String valorNovo,
        String usuario, OffsetDateTime dataEvento) {

    public static HistoricoResponse de(ProdutoHistorico h) {
        return new HistoricoResponse(h.getId(), h.getAcao(), h.getCampo(),
                h.getValorAnterior(), h.getValorNovo(),
                h.getUsuario() != null ? h.getUsuario().getNome() : "sistema",
                h.getDataEvento());
    }
}
