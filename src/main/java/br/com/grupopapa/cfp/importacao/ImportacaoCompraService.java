package br.com.grupopapa.cfp.importacao;

import br.com.grupopapa.cfp.domain.Fornecedor;
import br.com.grupopapa.cfp.domain.PedidoCompra;
import br.com.grupopapa.cfp.domain.TipoPedidoCompra;
import br.com.grupopapa.cfp.dto.CatalogoCompraResponse;
import br.com.grupopapa.cfp.dto.PedidoCompraRequest;
import br.com.grupopapa.cfp.exception.NegocioException;
import br.com.grupopapa.cfp.repository.ErpCompraRepository;
import br.com.grupopapa.cfp.security.UsuarioAutenticado;
import br.com.grupopapa.cfp.service.FornecedorService;
import br.com.grupopapa.cfp.service.PedidoCompraService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Pedido de compra por planilha: o modelo traz o cabecalho do pedido, a tabela de itens e
 * uma aba com os produtos do fornecedor. A importacao cria o pedido em digitacao (RASCUNHO)
 * pelas mesmas regras da tela (PedidoCompraService); o fornecedor confere e envia pelo portal.
 */
@Service
public class ImportacaoCompraService {

    static final String ABA = "PEDIDO";
    static final String ABA_PRODUTOS = "PRODUTOS";
    private static final String ABA_LISTAS = "LISTAS";
    private static final int LINHA_TITULOS = 8;     // 0-based: linha 9 do Excel
    private static final int LINHAS_MODELO = 300;   // limite do pedido de compra

    private static final String COL_CODIGO = "Código do produto";
    private static final String COL_DESCRICAO = "Descrição (preenchida automaticamente)";
    private static final String COL_QTD = "Quantidade";
    private static final String COL_PRECO = "Preço unitário (R$)";
    private static final String COL_OBS = "Observação do item";

    private final PedidoCompraService compraService;
    private final FornecedorService fornecedorService;

    public ImportacaoCompraService(PedidoCompraService compraService, FornecedorService fornecedorService) {
        this.compraService = compraService;
        this.fornecedorService = fornecedorService;
    }

    private Fornecedor fornecedor(UsuarioAutenticado logado, Long fornecedorIdInformado) {
        Long id = logado.isAdmin() ? fornecedorIdInformado : logado.getFornecedorId();
        if (id == null) {
            throw new NegocioException("Selecione o fornecedor no filtro antes de baixar ou importar a planilha.");
        }
        return fornecedorService.buscar(id);
    }

    // ================================================================ modelo

    @Transactional(readOnly = true)
    public byte[] modelo(UsuarioAutenticado logado, Long fornecedorId) {
        Fornecedor f = fornecedor(logado, fornecedorId);
        CatalogoCompraResponse catalogo = compraService.catalogo(logado, f.getId());
        List<ErpCompraRepository.Filial> filiais;
        try {
            filiais = compraService.filiais();
        } catch (DataAccessException e) {
            filiais = List.of();
        }

        Workbook wb = new XSSFWorkbook();
        Planilha.Estilos e = Planilha.estilos(wb);
        Sheet s = wb.createSheet(ABA);
        Sheet prod = wb.createSheet(ABA_PRODUTOS);
        Sheet listas = wb.createSheet(ABA_LISTAS);

        // ---------------------------------------------- produtos do fornecedor
        String[] titulosProd = {"Código", "Cód. fábrica", "Descrição", "Embalagem", "Unidade", "Origem"};
        for (int c = 0; c < titulosProd.length; c++) Planilha.escrever(prod, 0, c, titulosProd[c], e.cabecalho());
        int n = 1;
        for (CatalogoCompraResponse.Produto p : catalogo.produtos()) {
            Object codigo = p.codProdErp() != null ? p.codProdErp() : p.codigoFabrica();
            if (codigo == null) continue;
            Planilha.escrever(prod, n, 0, codigo, null);
            Planilha.escrever(prod, n, 1, p.codigoFabrica(), null);
            Planilha.escrever(prod, n, 2, p.descricao(), null);
            Planilha.escrever(prod, n, 3, p.embalagem(), null);
            Planilha.escrever(prod, n, 4, p.unidade(), null);
            Planilha.escrever(prod, n, 5, p.codProdErp() != null ? "WinThor" : "Produto novo do portal", null);
            n++;
        }
        int ultimaProduto = Math.max(n - 1, 1);
        int[] largurasProd = {12, 16, 55, 14, 9, 22};
        for (int c = 0; c < largurasProd.length; c++) prod.setColumnWidth(c, largurasProd[c] * 256);
        prod.createFreezePane(0, 1);
        prod.setAutoFilter(new CellRangeAddress(0, ultimaProduto, 0, titulosProd.length - 1));

        // ---------------------------------------------- listas (oculta)
        Planilha.escrever(listas, 0, 0, "Normal", null);
        Planilha.escrever(listas, 1, 0, "Bonificado", null);
        for (int i = 0; i < filiais.size(); i++) {
            Planilha.escrever(listas, i, 1, filiais.get(i).codigo() + " - " + filiais.get(i).nome(), null);
        }
        wb.setSheetHidden(wb.getSheetIndex(listas), true);

        // ---------------------------------------------- cabecalho do pedido
        Planilha.escrever(s, 0, 0, "PEDIDO DE COMPRA - " + f.getRazaoSocial()
                + (f.getCodFornecedorErp() != null ? " (cód. " + f.getCodFornecedorErp() + ")" : ""), e.titulo());
        Planilha.escrever(s, 1, 0, "Preencha o cabeçalho e os itens a partir da linha " + (LINHA_TITULOS + 2)
                + ". Escolha o código na lista ou consulte a aba PRODUTOS. Não altere os títulos.", e.subtitulo());

        DataValidationHelper h = s.getDataValidationHelper();
        String filialFixa = f.getCodFilialCompra();
        Planilha.escrever(s, 3, 0, "Filial de entrega", e.rotulo());
        if (filialFixa != null) {
            String nome = filiais.stream().filter(x -> x.codigo().equals(filialFixa)).map(ErpCompraRepository.Filial::nome)
                    .findFirst().orElse("");
            Planilha.escrever(s, 3, 1, filialFixa + (nome.isEmpty() ? "" : " - " + nome), e.travado());
            Planilha.escrever(s, 3, 2, "Definida pela equipe, não precisa alterar.", e.subtitulo());
        } else {
            Planilha.escrever(s, 3, 1, null, e.texto());
            if (!filiais.isEmpty()) {
                Planilha.validarIntervalo(s, h.createFormulaListConstraint(ABA_LISTAS + "!$B$1:$B$" + filiais.size()),
                        3, 3, 1, "Filial", "Escolha a filial na lista.", false);
            }
        }
        Planilha.escrever(s, 4, 0, "Tipo", e.rotulo());
        Planilha.escrever(s, 4, 1, "Normal", e.texto());
        Planilha.validarIntervalo(s, h.createFormulaListConstraint(ABA_LISTAS + "!$A$1:$A$2"), 4, 4, 1,
                "Tipo", "Escolha Normal ou Bonificado.", true);
        Planilha.escrever(s, 5, 0, "Previsão de entrega", e.rotulo());
        Planilha.escrever(s, 5, 1, null, e.data());
        Planilha.validarIntervalo(s, h.createDateConstraint(DataValidationConstraint.OperatorType.GREATER_OR_EQUAL,
                        "TODAY()", null, "dd/mm/yyyy"), 5, 5, 1, "Previsão de entrega",
                "Informe uma data a partir de hoje (dd/mm/aaaa).", false);
        Planilha.escrever(s, 6, 0, "Observação", e.rotulo());
        Planilha.escrever(s, 6, 1, null, e.texto());

        // ---------------------------------------------- itens
        String[] titulos = {COL_CODIGO + " *", COL_DESCRICAO, COL_QTD + " *", COL_PRECO + " *", COL_OBS};
        String[] ajudas = {
                "CODPROD do WinThor ou, para produto novo do portal, o código de fábrica. Escolha na lista ou veja a aba PRODUTOS.",
                "Não precisa preencher: vem da aba PRODUTOS.",
                "Quantidade na unidade do produto (coluna Embalagem da aba PRODUTOS).",
                "Preço unitário líquido, em reais. Ex.: 12,50",
                "Opcional. Até 150 caracteres."};
        int[] larguras = {18, 55, 14, 18, 40};
        Row tit = s.createRow(LINHA_TITULOS);
        tit.setHeightInPoints(30);
        for (int c = 0; c < titulos.length; c++) {
            Cell cell = tit.createCell(c);
            cell.setCellValue(titulos[c]);
            cell.setCellStyle(c == 1 || c == 4 ? e.cabecalhoOpcional() : e.cabecalho());
            Planilha.comentario(s, cell, ajudas[c]);
            s.setColumnWidth(c, larguras[c] * 256);
        }
        for (int r = LINHA_TITULOS + 1; r <= LINHA_TITULOS + LINHAS_MODELO; r++) {
            Row row = s.createRow(r);
            row.createCell(0).setCellStyle(e.inteiro());
            Cell desc = row.createCell(1);
            desc.setCellFormula("IF(A" + (r + 1) + "=\"\",\"\",IFERROR(VLOOKUP(A" + (r + 1) + "," + ABA_PRODUTOS
                    + "!$A$2:$C$" + (ultimaProduto + 1) + ",3,FALSE),\"Código não encontrado na aba PRODUTOS\"))");
            desc.setCellStyle(e.travado());
            row.createCell(2).setCellStyle(e.decimal());
            row.createCell(3).setCellStyle(e.moeda());
            row.createCell(4).setCellStyle(e.texto());
        }
        int ini = LINHA_TITULOS + 1, fim = LINHA_TITULOS + LINHAS_MODELO;
        if (n > 1) {
            Planilha.validarIntervalo(s, h.createFormulaListConstraint(ABA_PRODUTOS + "!$A$2:$A$" + (ultimaProduto + 1)),
                    ini, fim, 0, "Código do produto", "Código fora da lista de produtos do fornecedor.", false);
        }
        Planilha.validarIntervalo(s, h.createDecimalConstraint(DataValidationConstraint.OperatorType.GREATER_THAN, "0", null),
                ini, fim, 2, "Quantidade", "Informe uma quantidade maior que zero.", true);
        Planilha.validarIntervalo(s, h.createDecimalConstraint(DataValidationConstraint.OperatorType.GREATER_THAN, "0", null),
                ini, fim, 3, "Preço", "Informe um preço maior que zero.", true);
        s.setColumnWidth(0, Math.max(20, larguras[0]) * 256);
        s.createFreezePane(0, LINHA_TITULOS + 1);
        wb.setActiveSheet(0);
        return Planilha.gravar(wb);
    }

    // ================================================================ importacao

    @Transactional
    public ImportacaoResultado importar(MultipartFile arquivo, UsuarioAutenticado logado, Long fornecedorId) {
        Fornecedor f = fornecedor(logado, fornecedorId);
        CatalogoCompraResponse catalogo = compraService.catalogo(logado, f.getId());
        List<ErroImportacao> erros = new ArrayList<>();
        List<PedidoCompraRequest.Item> itens = new ArrayList<>();
        String codFilial = null;
        TipoPedidoCompra tipo = TipoPedidoCompra.NORMAL;
        LocalDate previsao = null;
        String observacao = null;

        // codigo -> produto do catalogo (CODPROD do WinThor ou codigo de fabrica)
        Map<String, CatalogoCompraResponse.Produto> porCodprod = new HashMap<>();
        Map<String, List<CatalogoCompraResponse.Produto>> porFabrica = new HashMap<>();
        for (CatalogoCompraResponse.Produto p : catalogo.produtos()) {
            if (p.codProdErp() != null) porCodprod.put(String.valueOf(p.codProdErp()), p);
            if (p.codigoFabrica() != null) {
                porFabrica.computeIfAbsent(Planilha.normalizar(p.codigoFabrica()), k -> new ArrayList<>()).add(p);
            }
        }

        try (Workbook wb = Planilha.abrir(arquivo)) {
            Sheet s = wb.getSheet(ABA) != null ? wb.getSheet(ABA) : wb.getSheetAt(0);

            Cell cFilial = Planilha.celulaAoLado(s, "Filial de entrega", LINHA_TITULOS);
            String filial = cFilial == null ? null : Planilha.texto(cFilial);
            if (filial != null) {
                String digitos = filial.replaceAll("^\\s*(\\d+).*$", "$1");
                if (!digitos.matches("\\d{1,2}")) {
                    erros.add(new ErroImportacao(cFilial.getRowIndex() + 1, "Filial de entrega", "Filial invalida: " + filial));
                } else {
                    codFilial = digitos;
                }
            }
            Cell cTipo = Planilha.celulaAoLado(s, "Tipo", LINHA_TITULOS);
            String t = cTipo == null ? null : Planilha.normalizar(Planilha.texto(cTipo));
            if (t != null && !t.isEmpty()) {
                if (t.startsWith("bonif")) tipo = TipoPedidoCompra.BONIFICADO;
                else if (!t.startsWith("normal")) {
                    erros.add(new ErroImportacao(cTipo.getRowIndex() + 1, "Tipo", "Use Normal ou Bonificado."));
                }
            }
            Cell cPrev = Planilha.celulaAoLado(s, "Previsao de entrega", LINHA_TITULOS);
            if (cPrev != null) {
                try {
                    previsao = Planilha.data(s.getRow(cPrev.getRowIndex()), 1);
                } catch (IllegalArgumentException ex) {
                    erros.add(new ErroImportacao(cPrev.getRowIndex() + 1, "Previsão de entrega", ex.getMessage()));
                }
            }
            Cell cObs = Planilha.celulaAoLado(s, "Observacao", LINHA_TITULOS);
            observacao = cObs == null ? null : Planilha.texto(cObs);
            if (observacao != null && observacao.length() > 500) {
                erros.add(new ErroImportacao(cObs.getRowIndex() + 1, "Observação", "No maximo 500 caracteres."));
            }

            Row titulos = Planilha.linhaDeTitulos(s, COL_CODIGO);
            if (titulos == null) {
                throw new NegocioException("Nao encontrei a coluna \"" + COL_CODIGO
                        + "\". Use o modelo de pedido de compra baixado do portal.");
            }
            Map<String, Integer> idx = Planilha.colunas(titulos);
            Integer iCod = idx.get(Planilha.normalizar(COL_CODIGO));
            Integer iQtd = idx.get(Planilha.normalizar(COL_QTD));
            Integer iPreco = idx.get(Planilha.normalizar(COL_PRECO));
            Integer iObs = idx.get(Planilha.normalizar(COL_OBS));
            if (iQtd == null || iPreco == null) {
                throw new NegocioException("Faltam as colunas de quantidade ou preco. Use o modelo baixado do portal.");
            }

            Map<String, Integer> primeiraLinha = new HashMap<>();
            for (int r = titulos.getRowNum() + 1; r <= s.getLastRowNum(); r++) {
                Row row = s.getRow(r);
                int linha = r + 1;
                String codigo = Planilha.texto(row, iCod);
                String qtdTxt = Planilha.texto(row, iQtd);
                String precoTxt = Planilha.texto(row, iPreco);
                if (codigo == null && qtdTxt == null && precoTxt == null) continue;
                if (itens.size() + erros.size() >= LINHAS_MODELO) {
                    erros.add(new ErroImportacao(linha, null, "O pedido aceita no maximo " + LINHAS_MODELO + " itens."));
                    break;
                }
                if (codigo == null) {
                    erros.add(new ErroImportacao(linha, COL_CODIGO, "Informe o codigo do produto."));
                    continue;
                }
                CatalogoCompraResponse.Produto p = porCodprod.get(codigo);
                if (p == null) {
                    List<CatalogoCompraResponse.Produto> fab = porFabrica.getOrDefault(Planilha.normalizar(codigo), List.of());
                    if (fab.size() == 1) p = fab.get(0);
                    else if (fab.size() > 1) {
                        erros.add(new ErroImportacao(linha, COL_CODIGO, "O codigo de fabrica " + codigo
                                + " aparece em mais de um produto: use o codigo (CODPROD) da aba PRODUTOS."));
                        continue;
                    }
                }
                if (p == null) {
                    erros.add(new ErroImportacao(linha, COL_CODIGO, "Produto " + codigo
                            + " nao encontrado entre os produtos deste fornecedor (aba PRODUTOS)."));
                    continue;
                }
                String chave = p.codProdErp() != null ? "W" + p.codProdErp() : "P" + p.produtoId();
                Integer anterior = primeiraLinha.putIfAbsent(chave, linha);
                if (anterior != null) {
                    erros.add(new ErroImportacao(linha, COL_CODIGO, "Produto " + codigo
                            + " repetido (ja esta na linha " + anterior + "). Some as quantidades em uma linha so."));
                    continue;
                }
                BigDecimal qtd, preco;
                try {
                    qtd = Planilha.numero(row, iQtd);
                    preco = Planilha.numero(row, iPreco);
                } catch (IllegalArgumentException ex) {
                    erros.add(new ErroImportacao(linha, null, ex.getMessage()));
                    continue;
                }
                if (qtd == null || qtd.signum() <= 0) {
                    erros.add(new ErroImportacao(linha, COL_QTD, "Informe uma quantidade maior que zero."));
                    continue;
                }
                if (preco == null || preco.signum() <= 0) {
                    erros.add(new ErroImportacao(linha, COL_PRECO, "Informe um preco maior que zero."));
                    continue;
                }
                if (qtd.scale() > 6 || preco.scale() > 6) {
                    erros.add(new ErroImportacao(linha, null, "Use no maximo 6 casas decimais."));
                    continue;
                }
                String obs = Planilha.texto(row, iObs);
                if (obs != null && obs.length() > 150) {
                    erros.add(new ErroImportacao(linha, COL_OBS, "No maximo 150 caracteres."));
                    continue;
                }
                itens.add(new PedidoCompraRequest.Item(p.codProdErp(), p.codProdErp() == null ? p.produtoId() : null,
                        qtd, preco, obs));
            }
        } catch (java.io.IOException ex) {
            throw new NegocioException("Nao foi possivel ler a planilha.");
        }

        if (!erros.isEmpty()) {
            erros.sort(Comparator.comparingInt(ErroImportacao::linha));
            throw new ImportacaoException("A planilha tem " + erros.size()
                    + " problema(s). Nada foi importado: corrija as linhas indicadas e importe de novo.", erros);
        }
        if (itens.isEmpty()) {
            throw new NegocioException("A planilha nao tem nenhum item preenchido.");
        }

        // mesmas regras da tela (filial permitida, previsao, produto do fornecedor no WinThor...)
        PedidoCompra pedido = compraService.criar(
                new PedidoCompraRequest(codFilial, tipo, previsao, observacao, itens), logado, f.getId());
        List<String> avisos = new ArrayList<>();
        avisos.add("Pedido " + pedido.getNumero() + " criado em digitacao com " + itens.size()
                + " item(ns). Confira e clique em Enviar para analise.");
        if (catalogo.avisoWinthor() != null) avisos.add(catalogo.avisoWinthor());
        return new ImportacaoResultado(pedido.getId(), pedido.getNumero(), itens.size(), avisos);
    }
}
