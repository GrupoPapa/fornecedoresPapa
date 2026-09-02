package br.com.grupopapa.cfp.repository;

import br.com.grupopapa.cfp.domain.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<Usuario> findByFornecedorIdOrderByNome(Long fornecedorId);

    @Query(value = """
           select u from Usuario u
           left join fetch u.fornecedor
           where (:termo is null
                  or upper(u.nome) like upper(concat('%', :termo, '%'))
                  or upper(u.email) like upper(concat('%', :termo, '%')))
           order by u.nome
           """,
           countQuery = """
           select count(u) from Usuario u
           where (:termo is null
                  or upper(u.nome) like upper(concat('%', :termo, '%'))
                  or upper(u.email) like upper(concat('%', :termo, '%')))
           """)
    Page<Usuario> buscar(@Param("termo") String termo, Pageable pageable);
}
