package br.com.grupopapa.cfp.importacao;

import br.com.grupopapa.cfp.exception.NegocioException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Leitura tolerante das planilhas preenchidas pelo fornecedor e utilitarios de montagem dos modelos. */
public final class Planilha {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private Planilha() { }

    // ------------------------------------------------------------------ leitura

    public static Workbook abrir(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new NegocioException("Selecione a planilha preenchida.");
        }
        if (arquivo.getSize() > MAX_BYTES) {
            throw new NegocioException("A planilha passa de 5 MB. Confira se e o modelo do portal.");
        }
        String nome = arquivo.getOriginalFilename() == null ? "" : arquivo.getOriginalFilename().toLowerCase();
        if (!nome.endsWith(".xlsx") && !nome.endsWith(".xls")) {
            throw new NegocioException("Envie a planilha em Excel (.xlsx), no modelo baixado do portal.");
        }
        try (InputStream in = arquivo.getInputStream()) {
            return WorkbookFactory.create(in);
        } catch (IOException | RuntimeException e) {
            throw new NegocioException("Nao foi possivel abrir a planilha. Salve como Excel (.xlsx) e tente de novo.");
        }
    }

    /** Texto sem acento, minusculo, sem "*" e com espacos simples: compara titulos de coluna. */
    public static String normalizar(String texto) {
        if (texto == null) return "";
        String s = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replace("*", " ").replaceAll("[\\s\\u00A0]+", " ").trim().toLowerCase(Locale.ROOT);
    }

    /** Procura, nas primeiras linhas, a linha de titulos que contem o texto informado. */
    public static Row linhaDeTitulos(Sheet sheet, String tituloObrigatorio) {
        String alvo = normalizar(tituloObrigatorio);
        int ultima = Math.min(sheet.getLastRowNum(), 30);
        for (int r = 0; r <= ultima; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell c : row) {
                if (normalizar(texto(c)).equals(alvo)) return row;
            }
        }
        return null;
    }

    /** Mapa titulo normalizado -> indice da coluna. */
    public static Map<String, Integer> colunas(Row titulos) {
        Map<String, Integer> mapa = new HashMap<>();
        for (Cell c : titulos) {
            String t = normalizar(texto(c));
            if (!t.isEmpty()) mapa.putIfAbsent(t, c.getColumnIndex());
        }
        return mapa;
    }

    /** Conteudo da celula como texto. Numero inteiro sai sem ",0" e sem notacao cientifica (EAN, NCM). */
    public static String texto(Cell c) {
        if (c == null) return null;
        CellType tipo = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
        String v = switch (tipo) {
            case STRING -> c.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(c)
                    ? c.getLocalDateTimeCellValue().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    : BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            default -> null;
        };
        if (v == null) return null;
        v = v.replace(' ', ' ').trim();
        return v.isEmpty() ? null : v;
    }

    public static String texto(Row row, Integer coluna) {
        return row == null || coluna == null ? null : texto(row.getCell(coluna));
    }

    /** Numero da celula. Aceita 1.234,56 / 1234,56 / 1234.56. Lanca IllegalArgumentException se nao for numero. */
    public static BigDecimal numero(Row row, Integer coluna) {
        if (row == null || coluna == null) return null;
        Cell c = row.getCell(coluna);
        if (c == null) return null;
        CellType tipo = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
        if (tipo == CellType.NUMERIC) {
            return BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros();
        }
        String t = texto(c);
        if (t == null) return null;
        t = t.replace("R$", "").replace(" ", "");
        if (t.contains(",")) t = t.replace(".", "").replace(",", ".");
        try {
            return new BigDecimal(t).stripTrailingZeros();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + texto(c) + "\" nao e um numero valido");
        }
    }

    /** Data da celula (data do Excel ou texto dd/mm/aaaa). Lanca IllegalArgumentException se invalida. */
    public static LocalDate data(Row row, Integer coluna) {
        if (row == null || coluna == null) return null;
        Cell c = row.getCell(coluna);
        if (c == null) return null;
        CellType tipo = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
        if (tipo == CellType.NUMERIC) {
            return c.getLocalDateTimeCellValue().toLocalDate();
        }
        String t = texto(c);
        if (t == null) return null;
        for (String formato : List.of("dd/MM/yyyy", "d/M/yyyy", "dd/MM/yy", "yyyy-MM-dd")) {
            try {
                return LocalDate.parse(t, DateTimeFormatter.ofPattern(formato));
            } catch (DateTimeParseException ignorada) {
                // tenta o proximo formato
            }
        }
        throw new IllegalArgumentException("\"" + t + "\" nao e uma data valida (use dd/mm/aaaa)");
    }

    /** Valor ao lado de um rotulo na coluna A (cabecalho do pedido: "Filial", "Tipo"...). */
    public static Cell celulaAoLado(Sheet sheet, String rotulo, int ateLinha) {
        String alvo = normalizar(rotulo);
        for (int r = 0; r <= Math.min(sheet.getLastRowNum(), ateLinha); r++) {
            Row row = sheet.getRow(r);
            if (row != null && normalizar(texto(row.getCell(0))).equals(alvo)) {
                return row.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ montagem dos modelos

    public record Estilos(CellStyle titulo, CellStyle subtitulo, CellStyle cabecalho, CellStyle cabecalhoOpcional,
                          CellStyle texto, CellStyle inteiro, CellStyle decimal, CellStyle moeda, CellStyle data,
                          CellStyle rotulo, CellStyle travado) { }

    public static Estilos estilos(Workbook wb) {
        DataFormat df = wb.createDataFormat();
        Font negrito = wb.createFont();
        negrito.setBold(true);
        Font tituloFonte = wb.createFont();
        tituloFonte.setBold(true);
        tituloFonte.setFontHeightInPoints((short) 14);
        tituloFonte.setColor(IndexedColors.DARK_RED.getIndex());
        Font branca = wb.createFont();
        branca.setBold(true);
        branca.setColor(IndexedColors.WHITE.getIndex());
        Font cinza = wb.createFont();
        cinza.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        cinza.setItalic(true);

        CellStyle titulo = wb.createCellStyle();
        titulo.setFont(tituloFonte);
        CellStyle subtitulo = wb.createCellStyle();
        subtitulo.setFont(cinza);

        CellStyle cab = wb.createCellStyle();
        cab.setFont(branca);
        cab.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
        cab.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        cab.setWrapText(true);
        cab.setVerticalAlignment(VerticalAlignment.CENTER);
        bordas(cab);
        CellStyle cabOpc = wb.createCellStyle();
        cabOpc.cloneStyleFrom(cab);
        cabOpc.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());

        CellStyle texto = wb.createCellStyle();
        texto.setDataFormat(df.getFormat("@"));
        bordas(texto);
        CellStyle inteiro = wb.createCellStyle();
        inteiro.setDataFormat(df.getFormat("0"));
        bordas(inteiro);
        CellStyle decimal = wb.createCellStyle();
        decimal.setDataFormat(df.getFormat("#,##0.0000"));
        bordas(decimal);
        CellStyle moeda = wb.createCellStyle();
        moeda.setDataFormat(df.getFormat("#,##0.00####"));
        bordas(moeda);
        CellStyle data = wb.createCellStyle();
        data.setDataFormat(df.getFormat("dd/mm/yyyy"));
        bordas(data);
        CellStyle rotulo = wb.createCellStyle();
        rotulo.setFont(negrito);
        CellStyle travado = wb.createCellStyle();
        travado.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        travado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        bordas(travado);
        return new Estilos(titulo, subtitulo, cab, cabOpc, texto, inteiro, decimal, moeda, data, rotulo, travado);
    }

    private static void bordas(CellStyle s) {
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    public static void escrever(Sheet sheet, int linha, int coluna, Object valor, CellStyle estilo) {
        Row row = sheet.getRow(linha);
        if (row == null) row = sheet.createRow(linha);
        Cell c = row.createCell(coluna);
        if (valor instanceof Number n) c.setCellValue(n.doubleValue());
        else if (valor instanceof LocalDate d) c.setCellValue(d);
        else if (valor != null) c.setCellValue(valor.toString());
        if (estilo != null) c.setCellStyle(estilo);
    }

    public static void comentario(Sheet sheet, Cell cell, String texto) {
        CreationHelper ch = sheet.getWorkbook().getCreationHelper();
        Drawing<?> desenho = sheet.createDrawingPatriarch();
        ClientAnchor ancora = ch.createClientAnchor();
        ancora.setCol1(cell.getColumnIndex());
        ancora.setCol2(cell.getColumnIndex() + 4);
        ancora.setRow1(cell.getRowIndex());
        ancora.setRow2(cell.getRowIndex() + 5);
        Comment c = desenho.createCellComment(ancora);
        c.setString(ch.createRichTextString(texto));
        cell.setCellComment(c);
    }

    /** Validacao do Excel (lista, inteiro ou decimal) num intervalo de linhas de uma coluna. */
    public static void validarIntervalo(Sheet sheet, DataValidationConstraint regra, int linhaIni, int linhaFim,
                                        int coluna, String titulo, String mensagem, boolean bloquear) {
        DataValidationHelper h = sheet.getDataValidationHelper();
        DataValidation v = h.createValidation(regra, new CellRangeAddressList(linhaIni, linhaFim, coluna, coluna));
        v.setShowErrorBox(true);
        v.setErrorStyle(bloquear ? DataValidation.ErrorStyle.STOP : DataValidation.ErrorStyle.WARNING);
        v.createErrorBox(titulo, mensagem);
        sheet.addValidationData(v);
    }

    public static byte[] gravar(Workbook wb) {
        try (Workbook w = wb; java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            w.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar a planilha", e);
        }
    }
}
