package br.com.grupopapa.cfp.repository;

import br.com.grupopapa.cfp.domain.Far;
import br.com.grupopapa.cfp.domain.StatusFar;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FarRepository extends JpaRepository<Far, Long> {

    @Query("""
           select f from Far f
           join fetch f.fornecedor
           join fetch f.motivo
           left join fetch f.usuarioBaixa
           left join fetch f.usuarioCriacao
           where f.id = :id
           """)
    Optional<Far> buscarCompleto(@Param("id") Long id);

    @Query(value = """
           select f from Far f
           join fetch f.fornecedor fo
           join fetch f.motivo m
           where (:fornecedorId is null or fo.id = :fornecedorId)
             and (:status is null or f.status = :status)
             and (:motivoId is null or m.id = :motivoId)
             and (:baixaFiscal is null or f.baixaFiscal = :baixaFiscal)
             and (:dataDe is null or f.dataVisita >= :dataDe)
             and (:dataAte is null or f.dataVisita <= :dataAte)
             and (:termo is null
                  or upper(f.numeroFar) like upper(concat('%', :termo, '%'))
                  or upper(coalesce(f.obsOcorrencia, '')) like upper(concat('%', :termo, '%'))
                  or upper(coalesce(f.codigoProduto, '')) like upper(concat('%', :termo, '%'))
                  or upper(coalesce(f.baixaFiscalReferencia, '')) like upper(concat('%', :termo, '%'))
                  or upper(fo.razaoSocial) like upper(concat('%', :termo, '%')))
           """,
           countQuery = """
           select count(f) from Far f
           join f.fornecedor fo
           join f.motivo m
           where (:fornecedorId is null or fo.id = :fornecedorId)
             and (:status is null or f.status = :status)
             and (:motivoId is null or m.id = :motivoId)
             and (:baixaFiscal is null or f.baixaFiscal = :baixaFiscal)
             and (:dataDe is null or f.dataVisita >= :dataDe)
             and (:dataAte is null or f.dataVisita <= :dataAte)
             and (:termo is null
                  or upper(f.numeroFar) like upper(concat('%', :termo, '%'))
                  or upper(coalesce(f.obsOcorrencia, '')) like upper(concat('%', :termo, '%'))
                  or upper(coalesce(f.codigoProduto, '')) like upper(concat('%', :termo, '%'))
                  or upper(coalesce(f.baixaFiscalReferencia, '')) like upper(concat('%', :termo, '%'))
                  or upper(fo.razaoSocial) like upper(concat('%', :termo, '%')))
           """)
    Page<Far> pesquisar(@Param("fornecedorId") Long fornecedorId,
                        @Param("status") StatusFar status,
                        @Param("motivoId") Long motivoId,
                        @Param("baixaFiscal") Boolean baixaFiscal,
                        @Param("dataDe") LocalDate dataDe,
                        @Param("dataAte") LocalDate dataAte,
                        @Param("termo") String termo,
                        Pageable pageable);

    @Query("""
           select f from Far f
           join fetch f.fornecedor fo
           join fetch f.motivo m
           left join fetch f.usuarioBaixa
           where (:fornecedorId is null or fo.id = :fornecedorId)
             and (:status is null or f.status = :status)
             and (:motivoId is null or m.id = :motivoId)
             and (:baixaFiscal is null or f.baixaFiscal = :baixaFiscal)
             and (:dataDe is null or f.dataVisita >= :dataDe)
             and (:dataAte is null or f.dataVisita <= :dataAte)
           order by f.dataVisita desc, f.id desc
           """)
    List<Far> listarParaExportacao(@Param("fornecedorId") Long fornecedorId,
                                   @Param("status") StatusFar status,
                                   @Param("motivoId") Long motivoId,
                                   @Param("baixaFiscal") Boolean baixaFiscal,
                                   @Param("dataDe") LocalDate dataDe,
                                   @Param("dataAte") LocalDate dataAte);

    @Query("""
           select case when count(f) > 0 then true else false end from Far f
           where f.fornecedor.id = :fornecedorId
             and upper(f.numeroFar) = upper(:numero)
             and (:ignorarId is null or f.id <> :ignorarId)
           """)
    boolean existeNumero(@Param("fornecedorId") Long fornecedorId,
                         @Param("numero") String numero,
                         @Param("ignorarId") Long ignorarId);
}
