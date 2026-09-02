package br.com.grupopapa.cfp.dto;

public record SenhaGeradaResponse(Long usuarioId, String email, Long codigoFornecedor,
                                  String senhaProvisoria) {
}
