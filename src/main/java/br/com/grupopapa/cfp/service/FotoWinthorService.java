package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.ProdutoFoto;
import br.com.grupopapa.cfp.repository.ProdutoFotoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Copia as fotos do produto para a pasta em que o WinThor guarda as imagens
 * (ex.: \\192.168.254.252\winthor\FOTOS), usando o CODPROD gerado na gravacao
 * como nome do arquivo - que e como o proprio WinThor localiza a foto.
 *
 * A pasta chega ao container como um volume (ver docker-compose) e o caminho
 * de dentro do container vai em CFP_FOTOS_WINTHOR_DIR. Sem essa variavel o
 * recurso fica desligado e nada muda no fluxo atual.
 */
@Service
public class FotoWinthorService {

    private static final Logger log = LoggerFactory.getLogger(FotoWinthorService.class);

    private static final Set<String> EXTENSOES = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");

    /** copiadas = quantidade gravada na pasta; erro = null quando deu tudo certo. */
    public record Resultado(int copiadas, String erro) {
        public boolean ok() { return erro == null; }
    }

    private final ProdutoFotoRepository fotoRepository;
    private final String diretorio;
    private final boolean copiarTodas;
    private final boolean sobrescrever;

    public FotoWinthorService(ProdutoFotoRepository fotoRepository,
                              @Value("${app.fotos.winthor.diretorio:}") String diretorio,
                              @Value("${app.fotos.winthor.copiar-todas:false}") boolean copiarTodas,
                              @Value("${app.fotos.winthor.sobrescrever:true}") boolean sobrescrever) {
        this.fotoRepository = fotoRepository;
        this.diretorio = (diretorio == null || diretorio.isBlank()) ? null : diretorio.trim();
        this.copiarTodas = copiarTodas;
        this.sobrescrever = sobrescrever;
    }

    public boolean isConfigurado() {
        return diretorio != null;
    }

    public String getDiretorio() {
        return diretorio;
    }

    /**
     * Grava as fotos do produto na pasta do WinThor. A principal vira CODPROD.ext;
     * as demais (quando app.fotos.winthor.copiar-todas = true) viram CODPROD_2.ext,
     * CODPROD_3.ext e assim por diante. Nunca lanca excecao: uma falha aqui nao
     * pode desfazer o cadastro que ja entrou no ERP.
     */
    @Transactional(readOnly = true)
    public Resultado copiar(Long produtoId, Long codProd) {
        if (!isConfigurado()) {
            return new Resultado(0, null);
        }
        if (codProd == null) {
            return new Resultado(0, "produto sem CODPROD");
        }

        List<ProdutoFoto> fotos = fotoRepository.findByProdutoId(produtoId).stream()
                .sorted(Comparator
                        .comparing((ProdutoFoto f) -> !Boolean.TRUE.equals(f.getPrincipal()))
                        .thenComparing(ProdutoFoto::getId))
                .toList();
        if (fotos.isEmpty()) {
            return new Resultado(0, null);
        }

        try {
            Path pasta = Path.of(diretorio);
            if (!Files.isDirectory(pasta)) {
                Files.createDirectories(pasta);
            }
            int copiadas = 0;
            for (int i = 0; i < fotos.size(); i++) {
                if (i > 0 && !copiarTodas) {
                    break;
                }
                ProdutoFoto foto = fotos.get(i);
                String nome = (i == 0 ? String.valueOf(codProd) : codProd + "_" + (i + 1))
                        + "." + extensao(foto);
                Path destino = pasta.resolve(nome);
                if (Files.exists(destino) && !sobrescrever) {
                    log.info("Foto {} ja existe na pasta do WinThor - mantida.", destino);
                    continue;
                }
                Files.write(destino, foto.getConteudo(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
                copiadas++;
                log.info("Foto do produto {} gravada em {}", produtoId, destino);
            }
            return new Resultado(copiadas, null);
        } catch (Exception e) {
            log.warn("Falha ao copiar as fotos do produto {} para {}: {}",
                    produtoId, diretorio, e.toString());
            return new Resultado(0, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    // ------------------------------------------------------------------ apoio

    /** Mantem a extensao original do arquivo; cai para jpg quando nao da para saber. */
    private String extensao(ProdutoFoto foto) {
        String nome = foto.getNomeArquivo();
        if (nome != null) {
            int ponto = nome.lastIndexOf('.');
            if (ponto > 0 && ponto < nome.length() - 1) {
                String ext = nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
                if (EXTENSOES.contains(ext)) {
                    return ext.equals("jpeg") ? "jpg" : ext;
                }
            }
        }
        String tipo = foto.getTipoConteudo() == null ? "" : foto.getTipoConteudo().toLowerCase(Locale.ROOT);
        if (tipo.contains("png")) return "png";
        if (tipo.contains("gif")) return "gif";
        if (tipo.contains("bmp")) return "bmp";
        if (tipo.contains("webp")) return "webp";
        return "jpg";
    }
}
