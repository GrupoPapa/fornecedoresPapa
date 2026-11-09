package br.com.grupopapa.cfp.importacao;

import br.com.grupopapa.cfp.domain.Fornecedor;
import br.com.grupopapa.cfp.domain.Pedido;
import br.com.grupopapa.cfp.dto.ProdutoRequest;
import br.com.grupopapa.cfp.exception.NegocioException;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import br.com.grupopapa.cfp.service.FornecedorService;
import br.com.grupopapa.cfp.service.PedidoService;
import br.com.grupopapa.cfp.service.ProdutoService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

/**
 * Cadastro de produtos por planilha: o portal gera o modelo (colunas conforme o tipo do
 * fornecedor) e importa o arquivo preenchido para o pedido de cadastro em aberto.
 * Cada linha passa pelas mesmas regras do formulario (ProdutoService). Se houver erro em
 * qualquer linha, nada e importado e a tela recebe a lista de erros por linha.
 */
@Service
public class ImportacaoProdutoService {

    static final String ABA = "PRODUTOS";
    private static final int LINHA_TITULOS = 3;   // 0-based: linha 4 do Excel
    private static final int LINHAS_MODELO = 500;
    private static final int MAX_ERROS = 200;

    /** Coluna do modelo. campo = nome no ProdutoRequest. */
    record Coluna(String campo, String titulo, boolean obrigatoria, Tipo tipo, int largura, String ajuda) { }

    enum Tipo { TEXTO, INTEIRO, DECIMAL }

    private static final List<Coluna> GERAL = List.of(
            new Coluna("codigo", "Código de fábrica", true, Tipo.TEXTO, 18, "Código do produto no seu sistema (até 30 caracteres). Não pode repetir."),
            new Coluna("descricao", "Descrição do produto", true, Tipo.TEXTO, 45, "Até 150 caracteres."),
            new Coluna("codBarraProduto", "Cód. barras do produto (EAN)", true, Tipo.TEXTO, 20, "EAN/GTIN de 8, 12, 13 ou 14 dígitos, só números."),
            new Coluna("codBarraCaixa", "Cód. barras da caixa master (DUN)", false, Tipo.TEXTO, 22, "Opcional. Diferente do EAN do produto."),
            new Coluna("ncm", "NCM", true, Tipo.TEXTO, 12, "Classificação fiscal com 8 dígitos, só números."),
            new Coluna("qtdEmbalagem", "Qtd. por embalagem / caixa master", true, Tipo.INTEIRO, 18, "Unidades dentro da caixa master. Número inteiro maior que zero."),
            new Coluna("pesoLiquido", "Peso líquido (kg)", true, Tipo.DECIMAL, 14, "Em kg por unidade. Ex.: 0,850"),
            new Coluna("pesoBruto", "Peso bruto (kg)", true, Tipo.DECIMAL, 14, "Em kg por unidade. Maior ou igual ao peso líquido."),
            new Coluna("observacao", "Observações", false, Tipo.TEXTO, 40, "Opcional. Até 500 caracteres."));

    private static final List<Coluna> PISO = List.of(
            new Coluna("codigo", "Código de fábrica", true, Tipo.TEXTO, 18, "Código do produto no seu sistema (até 30 caracteres). Não pode repetir."),
            new Coluna("descricao", "Descrição do produto", true, Tipo.TEXTO, 45, "Até 150 caracteres."),
            new Coluna("codBarraProduto", "Cód. barras do produto (EAN)", true, Tipo.TEXTO, 20, "EAN/GTIN de 8, 12, 13 ou 14 dígitos, só números."),
            new Coluna("codBarraCaixa", "Cód. barras da caixa (DUN)", false, Tipo.TEXTO, 22, "Opcional. Diferente do EAN do produto."),
            new Coluna("ncm", "NCM", true, Tipo.TEXTO, 12, "Classificação fiscal com 8 dígitos, só números."),
            new Coluna("m2Caixa", "m² por caixa", true, Tipo.DECIMAL, 13, "Metros quadrados que a caixa cobre. Ex.: 2,20"),
            new Coluna("m2Pallet", "m² por pallet", false, Tipo.DECIMAL, 13, "Metros quadrados por pallet. Maior ou igual ao m² por caixa."),
            new Coluna("pesoLiquido", "Peso/m² líquido (kg)", true, Tipo.DECIMAL, 14, "Em kg por m². Ex.: 18,5"),
            new Coluna("pesoBruto", "Peso/m² bruto (kg)", true, Tipo.DECIMAL, 14, "Em kg por m². Maior ou igual ao peso/m² líquido."),
            new Coluna("observacao", "Observações", false, Tipo.TEXTO, 40, "Opcional. Até 500 caracteres."));

    private final ProdutoService produtoService;
    private final PedidoService pedidoService;
    private final FornecedorService fornecedorService;
    private final Validator validator;

    public ImportacaoProdutoService(ProdutoService produtoService, PedidoService pedidoService,
                                    FornecedorService fornecedorService, Validator validator) {
        this.produtoService = produtoService;
        this.pedidoService = pedidoService;
        this.fornecedorService = fornecedorService;
        this.validator = validator;
    }

    private Fornecedor fornecedor(UsuarioAutenticado logado, Long fornecedorIdInformado) {
        Long id = logado.isAdmin() ? fornecedorIdInformado : logado.getFornecedorId();
        if (id == null) {
            throw new NegocioException("Selecione o fornecedor no filtro antes de baixar ou importar a planilha.");
        }
        return fornecedorService.buscar(id);
    }

    private static List<Coluna> colunasDo(Fornecedor f) {
        return f.getTipo().isPiso() ? PISO : GERAL;
    }

    // ================================================================ modelo

    @Transactional(readOnly = true)
    public byte[] modelo(UsuarioAutenticado logado, Long fornecedorId) {
        Fornecedor f = fornecedor(logado, fornecedorId);
        List<Coluna> colunas = colunasDo(f);
        Workbook wb = new XSSFWorkbook();
        Planilha.Estilos e = Planilha.estilos(wb);
        Sheet s = wb.createSheet(ABA);

        Planilha.escrever(s, 0, 0, "CADASTRO DE PRODUTOS - " + f.getRazaoSocial()
                + (f.getCodFornecedorErp() != null ? " (cód. " + f.getCodFornecedorErp() + ")" : ""), e.titulo());
        Planilha.escrever(s, 1, 0, "Preencha um produto por linha a partir da linha " + (LINHA_TITULOS + 2)
                + ". Colunas em vermelho são obrigatórias. Não altere os títulos nem a ordem das colunas.", e.subtitulo());
        Planilha.escrever(s, 2, 0, f.getTipo().isPiso()
                ? "Modelo para piso / revestimento: medidas por m²."
                : "Modelo geral: quantidade e peso por unidade.", e.subtitulo());

        Row titulos = s.createRow(LINHA_TITULOS);
        titulos.setHeightInPoints(32);
        for (int c = 0; c < colunas.size(); c++) {
            Coluna col = colunas.get(c);
            Cell cell = titulos.createCell(c);
            cell.setCellValue(col.titulo() + (col.obrigatoria() ? " *" : ""));
            cell.setCellStyle(col.obrigatoria() ? e.cabecalho() : e.cabecalhoOpcional());
            Planilha.comentario(s, cell, col.ajuda());
            s.setColumnWidth(c, col.largura() * 256);
            CellStyle estilo = switch (col.tipo()) {
                case TEXTO -> e.texto();
                case INTEIRO -> e.inteiro();
                case DECIMAL -> e.decimal();
            };
            s.setDefaultColumnStyle(c, estilo);
            for (int r = LINHA_TITULOS + 1; r <= LINHA_TITULOS + LINHAS_MODELO; r++) {
                Row row = s.getRow(r) == null ? s.createRow(r) : s.getRow(r);
                row.createCell(c).setCellStyle(estilo);
            }
            DataValidationHelper h = s.getDataValidationHelper();
            if (col.tipo() == Tipo.INTEIRO) {
                Planilha.validarIntervalo(s, h.createIntegerConstraint(DataValidationConstraint.OperatorType.GREATER_THAN, "0", null),
                        LINHA_TITULOS + 1, LINHA_TITULOS + LINHAS_MODELO, c, col.titulo(), "Informe um número inteiro maior que zero.", true);
            } else if (col.tipo() == Tipo.DECIMAL) {
                Planilha.validarIntervalo(s, h.createDecimalConstraint(DataValidationConstraint.OperatorType.GREATER_THAN, "0", null),
                        LINHA_TITULOS + 1, LINHA_TITULOS + LINHAS_MODELO, c, col.titulo(), "Informe um número maior que zero.", true);
            }
        }
        s.createFreezePane(1, LINHA_TITULOS + 1);

        Sheet inst = wb.createSheet("INSTRUÇÕES");
        Planilha.escrever(inst, 0, 0, "Como preencher", e.titulo());
        String[] passos = {
                "1. Preencha a aba PRODUTOS, um produto por linha. Linhas em branco são ignoradas.",
                "2. Códigos de barras e NCM: digite só os números (a coluna já está como texto para não perder zeros).",
                "3. Números com casas decimais podem usar vírgula: 0,850.",
                "4. Salve em Excel (.xlsx) e envie para a equipe, ou importe você mesmo no portal (Cadastro de produtos > Importar planilha).",
                "5. Se alguma linha tiver erro, o portal não importa nada e mostra a linha e o motivo para corrigir.",
                "6. Os produtos entram no pedido de cadastro em aberto. Confira no portal e clique em Enviar pedido."};
        for (int i = 0; i < passos.length; i++) Planilha.escrever(inst, 2 + i, 0, passos[i], null);
        Planilha.escrever(inst, 10, 0, "Coluna", e.cabecalho());
        Planilha.escrever(inst, 10, 1, "Obrigatória", e.cabecalho());
        Planilha.escrever(inst, 10, 2, "Como preencher", e.cabecalho());
        for (int i = 0; i < colunas.size(); i++) {
            Coluna col = colunas.get(i);
            Planilha.escrever(inst, 11 + i, 0, col.titulo(), e.rotulo());
            Planilha.escrever(inst, 11 + i, 1, col.obrigatoria() ? "Sim" : "Não", null);
            Planilha.escrever(inst, 11 + i, 2, col.ajuda(), null);
        }
        inst.setColumnWidth(0, 36 * 256);
        inst.setColumnWidth(1, 12 * 256);
        inst.setColumnWidth(2, 90 * 256);
        wb.setActiveSheet(0);
        return Planilha.gravar(wb);
    }

    // ================================================================ importacao

    @Transactional
    public ImportacaoResultado importar(MultipartFile arquivo, UsuarioAutenticado logado, Long fornecedorId) {
        Fornecedor f = fornecedor(logado, fornecedorId);
        List<Coluna> colunas = colunasDo(f);
        List<ErroImportacao> erros = new ArrayList<>();
        List<Map.Entry<Integer, ProdutoRequest>> linhas = new ArrayList<>();

        try (Workbook wb = Planilha.abrir(arquivo)) {
            Sheet s = wb.getSheet(ABA) != null ? wb.getSheet(ABA) : wb.getSheetAt(0);
            Row titulos = Planilha.linhaDeTitulos(s, colunas.get(0).titulo());
            if (titulos == null) {
                throw new NegocioException("Nao encontrei a coluna \"" + colunas.get(0).titulo()
                        + "\". Use o modelo baixado do portal, sem mudar os titulos.");
            }
            Map<String, Integer> indice = Planilha.colunas(titulos);
            Map<String, Integer> porCampo = new HashMap<>();
            for (Coluna col : colunas) {
                Integer i = indice.get(Planilha.normalizar(col.titulo()));
                if (i == null && col.obrigatoria()) {
                    erros.add(new ErroImportacao(titulos.getRowNum() + 1, col.titulo(),
                            "Coluna nao encontrada. " + (f.getTipo().isPiso()
                                    ? "Este fornecedor usa o modelo de piso / revestimento."
                                    : "Este fornecedor usa o modelo geral.") + " Baixe o modelo de novo."));
                }
                porCampo.put(col.campo(), i);
            }
            if (!erros.isEmpty()) {
                throw new ImportacaoException("A planilha nao esta no modelo deste fornecedor.", erros);
            }

            for (int r = titulos.getRowNum() + 1; r <= s.getLastRowNum(); r++) {
                Row row = s.getRow(r);
                int linhaExcel = r + 1;
                if (vazia(row, porCampo)) continue;
                if (linhas.size() >= LINHAS_MODELO) {
                    erros.add(new ErroImportacao(linhaExcel, null, "No maximo " + LINHAS_MODELO + " produtos por planilha."));
                    break;
                }
                ProdutoRequest req = ler(row, linhaExcel, porCampo, colunas, erros);
                if (req != null) linhas.add(Map.entry(linhaExcel, req));
            }
        } catch (java.io.IOException e) {
            throw new NegocioException("Nao foi possivel ler a planilha.");
        }

        if (linhas.isEmpty() && erros.isEmpty()) {
            throw new NegocioException("A planilha nao tem nenhum produto preenchido.");
        }

        // regras do formulario (validacao dos campos + ProdutoService: duplicidade, EAN, piso...)
        Pedido pedido = pedidoService.abrirOuObter(logado, f.getId());
        int importados = 0;
        for (Map.Entry<Integer, ProdutoRequest> l : linhas) {
            if (erros.size() >= MAX_ERROS) break;
            Set<ConstraintViolation<ProdutoRequest>> violacoes = validator.validate(l.getValue());
            if (!violacoes.isEmpty()) {
                for (ConstraintViolation<ProdutoRequest> v : violacoes) {
                    erros.add(new ErroImportacao(l.getKey(), tituloDo(v.getPropertyPath().toString(), colunas), v.getMessage()));
                }
                continue;
            }
            try {
                produtoService.criar(l.getValue(), logado, null, pedido.getId());
                importados++;
            } catch (NegocioException e) {
                erros.add(new ErroImportacao(l.getKey(), null, e.getMessage()));
            }
        }
        if (!erros.isEmpty()) {
            erros.sort(Comparator.comparingInt(ErroImportacao::linha));
            // a excecao desfaz a transacao inteira: nenhum produto fica gravado
            throw new ImportacaoException("A planilha tem " + erros.size()
                    + " problema(s). Nada foi importado: corrija as linhas indicadas e importe de novo.", erros);
        }
        return new ImportacaoResultado(pedido.getId(), pedido.getNumero(), importados,
                List.of("Os produtos entraram no pedido " + pedido.getNumero()
                        + ". Confira e clique em Enviar pedido quando estiver tudo certo."));
    }

    private boolean vazia(Row row, Map<String, Integer> porCampo) {
        if (row == null) return true;
        for (Integer i : porCampo.values()) {
            if (i != null && Planilha.texto(row, i) != null) return false;
        }
        return true;
    }

    private ProdutoRequest ler(Row row, int linha, Map<String, Integer> porCampo, List<Coluna> colunas,
                               List<ErroImportacao> erros) {
        Map<String, String> textos = new HashMap<>();
        Map<String, BigDecimal> numeros = new HashMap<>();
        boolean ok = true;
        for (Coluna col : colunas) {
            Integer i = porCampo.get(col.campo());
            if (col.tipo() == Tipo.TEXTO) {
                String t = Planilha.texto(row, i);
                if (t != null && ("codBarraProduto".equals(col.campo()) || "codBarraCaixa".equals(col.campo())
                        || "ncm".equals(col.campo()))) {
                    t = t.replaceAll("[\\s.\\-]", ""); // 7891.234-5 -> 78912345
                }
                textos.put(col.campo(), t);
            } else {
                try {
                    numeros.put(col.campo(), Planilha.numero(row, i));
                } catch (IllegalArgumentException e) {
                    erros.add(new ErroImportacao(linha, col.titulo(), e.getMessage()));
                    ok = false;
                }
            }
        }
        if (!ok) return null;
        Integer qtdEmbalagem = null;
        BigDecimal qtd = numeros.get("qtdEmbalagem");
        if (qtd != null) {
            if (qtd.stripTrailingZeros().scale() > 0) {
                erros.add(new ErroImportacao(linha, tituloDo("qtdEmbalagem", colunas), "Informe um numero inteiro."));
                return null;
            }
            qtdEmbalagem = qtd.intValue();
        }
        return new ProdutoRequest(
                textos.get("codigo"), textos.get("descricao"), textos.get("codBarraCaixa"), textos.get("codBarraProduto"),
                textos.get("ncm"), numeros.get("pesoLiquido"), numeros.get("pesoBruto"),
                null, null, qtdEmbalagem, numeros.get("m2Caixa"), numeros.get("m2Pallet"), textos.get("observacao"));
    }

    private static String tituloDo(String campo, List<Coluna> colunas) {
        return colunas.stream().filter(c -> c.campo().equals(campo)).map(Coluna::titulo).findFirst().orElse(campo);
    }
}
