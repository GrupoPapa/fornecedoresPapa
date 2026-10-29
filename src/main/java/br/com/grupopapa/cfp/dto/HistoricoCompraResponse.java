package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.PedidoCompraHistorico;

import java.time.OffsetDateTime;

public record HistoricoCompraResponse(Long id, String evento, String descricao, String usuario,
                                      OffsetDateTime dataEvento) {

    public static HistoricoCompraResponse de(PedidoCompraHistorico h) {
        return new HistoricoCompraResponse(h.getId(), h.getEvento(), h.getDescricao(),
                h.getUsuario() != null ? h.getUsuario().getNome() : "sistema", h.getDataEvento());
    }
}
