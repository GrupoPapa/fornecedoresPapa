package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.Produto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ExportacaoService {

    private static final String[] CABECALHO = {
            "Fornecedor", "CNPJ", "Tipo fornecedor", "Codigo", "Descricao Fornecedor (Produto)",
            "Cod. barra caixa MASTER (DUN)", "Cod. barra produto (EAN)", "Classificacao Fiscal (NCM)",
            "Peso liquido (kg ou kg/m2)", "Peso bruto (kg ou kg/m2)",
            "Metro Quadrado Caixa", "Metro Quadrado Pallet", "Qtd. embalagem / caixa MASTER", "m2/Caixa", "m2/Pallet",
            "Status", "Data de envio"
    };

    private static String rotuloTipo(Produto p) {
        return p.getFornecedor().getTipo().isPiso() ? "Piso/Revestimento" : "Geral";
    }

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] gerarExcel(List<Produto> produtos) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("CAD PROD");

            CellStyle estiloCabecalho = wb.createCellStyle();
            Font fonte = wb.createFont();
            fonte.setBold(true);
            fonte.setColor(IndexedColors.WHITE.getIndex());
            estiloCabecalho.setFont(fonte);
            estiloCabecalho.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
            estiloCabecalho.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            estiloCabecalho.setAlignment(HorizontalAlignment.CENTER);
            estiloCabecalho.setBorderBottom(BorderStyle.THIN);

            CellStyle estiloTexto = wb.createCellStyle();
            estiloTexto.setDataFormat(wb.createDataFormat().getFormat("@"));

            CellStyle estiloDecimal = wb.createCellStyle();
            estiloDecimal.setDataFormat(wb.createDataFormat().getFormat("#,##0.0000"));

            Row cabecalho = sheet.createRow(0);
            for (int i = 0; i < CABECALHO.length; i++) {
                Cell c = cabecalho.createCell(i);
                c.setCellValue(CABECALHO[i]);
                c.setCellStyle(estiloCabecalho);
            }

            int linha = 1;
            for (Produto p : produtos) {
                Row row = sheet.createRow(linha++);
                int col = 0;
                row.createCell(col++).setCellValue(p.getFornecedor().getRazaoSocial());
                criarTexto(row, col++, p.getFornecedor().getCnpj(), estiloTexto);
                row.createCell(col++).setCellValue(rotuloTipo(p));
                criarTexto(row, col++, p.getCodigo(), estiloTexto);
                row.createCell(col++).setCellValue(p.getDescricao());
                criarTexto(row, col++, p.getCodBarraCaixa(), estiloTexto);
                criarTexto(row, col++, p.getCodBarraProduto(), estiloTexto);
                criarTexto(row, col++, p.getNcm(), estiloTexto);
                criarDecimal(row, col++, p.getPesoLiquido(), estiloDecimal);
                criarDecimal(row, col++, p.getPesoBruto(), estiloDecimal);
                criarDecimal(row, col++, p.getMetroCaixa(), estiloDecimal);
                criarDecimal(row, col++, p.getMetroPallet(), estiloDecimal);
                if (p.getQtdEmbalagem() != null) {
                    row.createCell(col).setCellValue(p.getQtdEmbalagem());
                }
                col++;
                criarDecimal(row, col++, p.getM2Caixa(), estiloDecimal);
                criarDecimal(row, col++, p.getM2Pallet(), estiloDecimal);
                row.createCell(col++).setCellValue(p.getStatus().name());
                row.createCell(col).setCellValue(formatar(p.getDataEnvio()));
            }

            for (int i = 0; i < CABECALHO.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.createFreezePane(0, 1);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar o arquivo Excel", e);
        }
    }

    public byte[] gerarCsv(List<Produto> produtos) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // BOM para o Excel abrir em UTF-8
        sb.append(String.join(";", CABECALHO)).append('\n');
        for (Produto p : produtos) {
            sb.append(csv(p.getFornecedor().getRazaoSocial())).append(';')
              .append(csv(p.getFornecedor().getCnpj())).append(';')
              .append(csv(rotuloTipo(p))).append(';')
              .append(csv(p.getCodigo())).append(';')
              .append(csv(p.getDescricao())).append(';')
              .append(csv(p.getCodBarraCaixa())).append(';')
              .append(csv(p.getCodBarraProduto())).append(';')
              .append(csv(p.getNcm())).append(';')
              .append(numero(p.getPesoLiquido())).append(';')
              .append(numero(p.getPesoBruto())).append(';')
              .append(numero(p.getMetroCaixa())).append(';')
              .append(numero(p.getMetroPallet())).append(';')
              .append(p.getQtdEmbalagem() == null ? "" : p.getQtdEmbalagem()).append(';')
              .append(numero(p.getM2Caixa())).append(';')
              .append(numero(p.getM2Pallet())).append(';')
              .append(p.getStatus().name()).append(';')
              .append(formatar(p.getDataEnvio()))
              .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void criarTexto(Row row, int col, String valor, CellStyle estilo) {
        Cell c = row.createCell(col);
        c.setCellStyle(estilo);
        c.setCellValue(valor == null ? "" : valor);
    }

    private void criarDecimal(Row row, int col, BigDecimal valor, CellStyle estilo) {
        Cell c = row.createCell(col);
        if (valor != null) {
            c.setCellValue(valor.doubleValue());
            c.setCellStyle(estilo);
        }
    }

    private String csv(String valor) {
        if (valor == null) return "";
        return valor.replace(';', ',').replace('\n', ' ').replace('\r', ' ');
    }

    private String numero(BigDecimal valor) {
        return valor == null ? "" : valor.toPlainString().replace('.', ',');
    }

    private String formatar(OffsetDateTime data) {
        return data == null ? "" : data.format(DATA_HORA);
    }
}
