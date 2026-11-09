package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.FarHistorico;

import java.time.OffsetDateTime;

public record HistoricoFarResponse(Long id, String evento, String descricao, String usuario,
                                   OffsetDateTime dataEvento) {

    public static HistoricoFarResponse de(FarHistorico h) {
        return new HistoricoFarResponse(h.getId(), h.getEvento(), h.getDescricao(),
                h.getUsuario() != null ? h.getUsuario().getNome() : "sistema", h.getDataEvento());
    }
}
