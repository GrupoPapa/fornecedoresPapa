package br.com.grupopapa.cfp.dto;

import java.util.List;

/**
 * Resultado da conferencia de EAN, DUN e codigo de fabrica (portal + WinThor).
 * campo: codigo, codBarraProduto ou codBarraCaixa (nome do campo no formulario).
 * winthorConsultado = false quando o WinThor nao respondeu (a conferencia la fica para depois).
 */
public record ConferenciaCodigosResponse(List<Conflito> conflitos, boolean winthorConsultado) {

    public record Conflito(String campo, String mensagem) { }

    public boolean ok() { return conflitos.isEmpty(); }
}
