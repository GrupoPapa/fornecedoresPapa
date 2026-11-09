package br.com.grupopapa.cfp.dto;

import br.com.grupopapa.cfp.domain.Perfil;
import br.com.grupopapa.cfp.domain.TipoFornecedor;
import br.com.grupopapa.cfp.domain.Usuario;

import java.time.OffsetDateTime;

public record UsuarioResponse(
        Long id, String nome, String email, Perfil perfil,
        Long fornecedorId, String fornecedorNome, TipoFornecedor fornecedorTipo,
        Boolean ativo, Boolean trocarSenha, OffsetDateTime ultimoAcesso,
        Boolean farHabilitado) {

    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(
                u.getId(), u.getNome(), u.getEmail(), u.getPerfil(),
                u.getFornecedor() != null ? u.getFornecedor().getId() : null,
                u.getFornecedor() != null ? u.getFornecedor().getRazaoSocial() : null,
                u.getFornecedor() != null ? u.getFornecedor().getTipo() : null,
                u.getAtivo(), u.getTrocarSenha(), u.getDataUltimoAcesso(),
                u.isAdmin() || (u.getFornecedor() != null && u.getFornecedor().isFarHabilitado()));
    }
}
