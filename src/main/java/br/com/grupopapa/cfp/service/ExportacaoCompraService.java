package br.com.grupopapa.cfp.service;

import br.com.grupopapa.cfp.domain.PedidoCompra;
import br.com.grupopapa.cfp.domain.PedidoCompraItem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Exporta os pedidos de compra (uma linha por item) em Excel ou CSV. */
@Service
public class ExportacaoCompraService {

    private static final String[] CABECALHO = {
            "Pedido", "Fornecedor", "Cod. fornecedor WinThor", "Situacao", "Filial", "Tipo",
            "Previsao entrega", "Enviado em", "Sugestao WinThor",
            "Item", "CODPROD", "Cod. fabrica", "Descricao", "Embalagem", "Unidade",
            "Quantidade", "Preco unitario", "Total item", "Ultimo preco", "Divergencia"
    };

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private List<Object[]> linhas(List<PedidoCompra> pedidos) {
        List<Object[]> linhas = new ArrayList<>();
        for (PedidoCompra p : pedidos) {
            for (PedidoCompraItem i : p.getItens()) {
                linhas.add(new Object[]{
                        p.getNumero(), p.getFornecedor().getRazaoSocial(), p.getFornecedor().getCodFornecedorErp(),
                        p.getStatus().name(), p.getCodFilial(), p.getTipo().name(),
                        p.getDataPrevisaoEntrega() == null ? null : p.getDataPrevisaoEntrega().format(DATA),
                        p.getDataEnvio() == null ? null : p.getDataEnvio().format(DATA_HORA),
                        p.getNumSugestaoErp(),
                        i.getSeq(), i.getCodProdErp(), i.getCodigoFabrica(), i.getDescricao(), i.getEmbalagem(),
                        i.getUnidade(), i.getQuantidade(), i.getPrecoUnitario(), i.getValorTotal(),
                        i.getUltimoPreco(), i.getDivergencia()});
            }
        }
        return linhas;
    }

    public byte[] gerarExcel(List<PedidoCompra> pedidos) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("PEDIDOS DE COMPRA");
            CellStyle cab = wb.createCellStyle();
            Font fonte = wb.createFont();
            fonte.setBold(true);
            fonte.setColor(IndexedColors.WHITE.getIndex());
            cab.setFont(fonte);
            cab.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
            cab.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle decimal = wb.createCellStyle();
            decimal.setDataFormat(wb.createDataFormat().getFormat("#,##0.00####"));

            Row r0 = sheet.createRow(0);
            for (int c = 0; c < CABECALHO.length; c++) {
                Cell cell = r0.createCell(c);
                cell.setCellValue(CABECALHO[c]);
                cell.setCellStyle(cab);
            }
            int n = 1;
            for (Object[] linha : linhas(pedidos)) {
                Row r = sheet.createRow(n++);
                for (int c = 0; c < linha.length; c++) {
                    Object v = linha[c];
                    if (v == null) continue;
                    Cell cell = r.createCell(c);
                    if (v instanceof BigDecimal b) {
                        cell.setCellValue(b.doubleValue());
                        cell.setCellStyle(decimal);
                    } else if (v instanceof Number num) {
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

    public byte[] gerarCsv(List<PedidoCompra> pedidos) {
        StringBuilder sb = new StringBuilder("﻿").append(String.join(";", CABECALHO)).append("\r\n");
        for (Object[] linha : linhas(pedidos)) {
            for (int c = 0; c < linha.length; c++) {
                if (c > 0) sb.append(';');
                Object v = linha[c];
                if (v == null) continue;
                String texto = v instanceof BigDecimal b ? b.stripTrailingZeros().toPlainString().replace('.', ',') : v.toString();
                if (texto.contains(";") || texto.contains("\"") || texto.contains("\n")) {
                    texto = "\"" + texto.replace("\"", "\"\"") + "\"";
                }
                sb.append(texto);
            }
            sb.append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
