package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.FarMotivo;

public record FarMotivoResponse(Long id, String descricao, Boolean ativo) {

    public static FarMotivoResponse de(FarMotivo m) {
        return new FarMotivoResponse(m.getId(), m.getDescricao(), m.getAtivo());
    }
}
