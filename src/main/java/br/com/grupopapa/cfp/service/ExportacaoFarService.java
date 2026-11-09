package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.Far;
import br.com.grupopapa.cfp.domain.StatusFar;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exporta o controle de avarias no mesmo formato da planilha usada hoje. */
@Service
public class ExportacaoFarService {

    private static final String[] CABECALHO = {
            "Fornecedor", "Cod. fornecedor WinThor",
            "Data visita", "Qtd produtos", "Motivo", "Obs. ocorrencia",
            "Data abertura FAR", "N. FAR", "Status", "Obs. FAR fornecedor", "Obs. FAR empresa",
            "Comprovante pagamento", "Vinculo baixa fiscal", "Referencia baixa fiscal", "Data vinculo", "Vinculado por"
    };

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private List<Object[]> linhas(List<Far> fars, Map<Long, Long> comprovantes) {
        List<Object[]> linhas = new ArrayList<>();
        for (Far f : fars) {
            String status = f.getStatus() == StatusFar.OUTROS && f.getStatusOutros() != null
                    ? "Outros - " + f.getStatusOutros() : f.getStatus().getRotulo();
            long qtdComprovantes = comprovantes.getOrDefault(f.getId(), 0L);
            linhas.add(new Object[]{
                    f.getFornecedor().getRazaoSocial(), f.getFornecedor().getCodFornecedorErp(),
                    f.getDataVisita() == null ? null : f.getDataVisita().format(DATA),
                    f.getQtdProdutos(), f.getMotivo().getDescricao(), f.getObsOcorrencia(),
                    f.getDataAberturaFar() == null ? null : f.getDataAberturaFar().format(DATA),
                    f.getNumeroFar(), status, f.getObsFarFornecedor(), f.getObsFarEmpresa(),
                    qtdComprovantes > 0 ? "Sim" : (f.getStatus() == StatusFar.FINALIZADO_APROVADO ? "Pendente" : "Nao"),
                    Boolean.TRUE.equals(f.getBaixaFiscal()) ? "Sim" : "Nao",
                    f.getBaixaFiscalReferencia(),
                    f.getDataBaixaFiscal() == null ? null : f.getDataBaixaFiscal().format(DATA_HORA),
                    f.getUsuarioBaixa() == null ? null : f.getUsuarioBaixa().getNome()});
        }
        return linhas;
    }

    public byte[] gerarExcel(List<Far> fars, Map<Long, Long> comprovantes) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("CONTROLE DE AVARIAS");
            CellStyle cab = wb.createCellStyle();
            Font fonte = wb.createFont();
            fonte.setBold(true);
            fonte.setColor(IndexedColors.WHITE.getIndex());
            cab.setFont(fonte);
            cab.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
            cab.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row r0 = sheet.createRow(0);
            for (int c = 0; c < CABECALHO.length; c++) {
                Cell cell = r0.createCell(c);
                cell.setCellValue(CABECALHO[c]);
                cell.setCellStyle(cab);
            }
            int n = 1;
            for (Object[] linha : linhas(fars, comprovantes)) {
                Row r = sheet.createRow(n++);
                for (int c = 0; c < linha.length; c++) {
                    Object v = linha[c];
                    if (v == null) continue;
                    Cell cell = r.createCell(c);
                    if (v instanceof Number num) {
                        cell.setCellValue(num.doubleValue());
                    } else {
                        cell.setCellValue(v.toString());
                    }
                }
            }
            sheet.createFreezePane(0, 1);
            for (int c = 0; c < CABECALHO.length; c++) {
                sheet.autoSizeColumn(c);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar o Excel", e);
        }
    }

    public byte[] gerarCsv(List<Far> fars, Map<Long, Long> comprovantes) {
        StringBuilder sb = new StringBuilder("﻿").append(String.join(";", CABECALHO)).append("\r\n");
        for (Object[] linha : linhas(fars, comprovantes)) {
            for (int c = 0; c < linha.length; c++) {
                if (c > 0) sb.append(';');
                Object v = linha[c];
                if (v == null) continue;
                String texto = v.toString();
                if (texto.contains(";") || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
                    texto = "\"" + texto.replace("\"", "\"\"") + "\"";
                }
                sb.append(texto);
            }
            sb.append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
