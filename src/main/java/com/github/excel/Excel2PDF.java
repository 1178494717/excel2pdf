package com.github.excel;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * @date 2020/5/20
 */
public class Excel2PDF {

    private HSSFSheet sheet;

    private HSSFPalette customPalette;

    private Document document;

    private float rate;

    private float excelWidth;

    private int lastCellNum;

    public Excel2PDF(InputStream inputStream) throws IOException {
        HSSFWorkbook workbook = new HSSFWorkbook(inputStream);
        this.sheet = workbook.getSheetAt(0);
        this.customPalette = workbook.getCustomPalette();
    }

    public Excel2PDF(InputStream inputStream, OutputStream outputStream) throws IOException {
        this(inputStream);
        PdfDocument pdfDocument = new PdfDocument(new PdfWriter(outputStream));
        this.document = new Document(pdfDocument, PageSize.A4.rotate());
        this.rate = getRate();
        this.lastCellNum = this.sheet.getRow(0).getLastCellNum();
    }

    /**
     * 转换
     *
     * @throws IOException
     */
    public void convert() throws IOException {
        Table table = new Table(getColumnWidths());
        doRowAndCell(table);
        doPicture(table);
        document.add(table);
        document.close();
    }

    /**
     * 处理图片
     *
     * @param table
     */
    private void doPicture(Table table) {
        HSSFPatriarch drawingPatriarch = sheet.getDrawingPatriarch();
        if(drawingPatriarch != null){
            List<HSSFShape> children = drawingPatriarch.getChildren();
            for (HSSFShape shape : children){
                HSSFPicture hssfPicture = (HSSFPicture)shape;
                table.setNextRenderer(new OverlappingImageTableRenderer(table, hssfPicture, sheet));
            }
        }
    }

    /**
     * 处理行列
     *
     * @param table
     * @throws IOException
     */
    private void doRowAndCell(Table table) throws IOException {
        int lastRowNum = sheet.getLastRowNum();
        for (int i = 0; i < lastRowNum; i++) {
            HSSFRow row = sheet.getRow(i);
            for (int j = 0; j < lastCellNum; j++) {
                HSSFCell cell = row.getCell(j);
                if (cell != null) {
                    if (!isUse(cell)) {
                        CellRangeAddress cellRangeAddress = getCellRangeAddress(cell);
                        int rowspan = 1;
                        int colspan = 1;
                        if (cellRangeAddress != null) {
                            colspan = cellRangeAddress.getLastColumn() - cellRangeAddress.getFirstColumn() + 1;
                            rowspan = cellRangeAddress.getLastRow() - cellRangeAddress.getFirstRow() + 1;
                            j = cellRangeAddress.getLastColumn();
                        }
                        Cell pdfCell = transformCommon(cell, rowspan, colspan);
                        table.addCell(pdfCell);
                    }
                } else {
                    // 补偿
                    table.addCell("");
                }
            }
        }
    }

    /**
     * 处理每一个单元格
     *
     * @param cell
     * @param rowspan
     * @param colspan
     * @return
     * @throws IOException
     */
    private Cell transformCommon(HSSFCell cell, int rowspan, int colspan) throws IOException {
        String value = Excel2PdfHelper.getValue(cell);

        Cell pdfCell = new Cell(rowspan, colspan)
                .setHeight(cell.getRow().getHeight() * this.rate * 1.2f)
                .setPadding(0);
        Text text = new Text(value);
        setPdfCellFont(cell, text);
        pdfCell.add(new Paragraph(text).setPadding(0f).setMargin(0f));
        HSSFCellStyle cellStyle = cell.getCellStyle();
        // 布局
        VerticalAlignment verticalAlignment = cellStyle.getVerticalAlignment();
        pdfCell.setVerticalAlignment(Excel2PdfHelper.getVerticalAlignment(verticalAlignment));
        HorizontalAlignment alignment = cellStyle.getAlignment();
        pdfCell.setTextAlignment(Excel2PdfHelper.getTextAlignment(alignment, cell.getCellType()));

        //边框
        Excel2PdfHelper.transformBorder(cell, pdfCell);

        //背景色
        short colorIndex = cellStyle.getFillForegroundColor();
        HSSFColor color = this.customPalette.getColor(colorIndex);
        if (color.getIndex() != 64) {
            short[] triplet = color.getTriplet();
            pdfCell.setBackgroundColor(new DeviceRgb(triplet[0], triplet[1], triplet[2]));
        }
        return pdfCell;
    }

    /**
     * 处理单元格字体样式
     *
     * @param cell
     * @param text
     * @throws IOException
     */
    private void setPdfCellFont(HSSFCell cell, Text text) throws IOException {
        HSSFCellStyle cellStyle = cell.getCellStyle();
        //字体大小
        HSSFFont font = cellStyle.getFont(cell.getSheet().getWorkbook());
        short fontHeight = font.getFontHeight();
        text.setFont(PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H", true));
        text.setFontSize(fontHeight * rate * 1.05f);

        //字体颜色
        HSSFColor hssfColor = font.getHSSFColor(cell.getSheet().getWorkbook());
        if (hssfColor != null && hssfColor.getIndex() != 64) {
            short[] triplet = hssfColor.getTriplet();
            text.setFontColor(new DeviceRgb(triplet[0], triplet[1], triplet[2]));
        }

        //加粗
        if (font.getBold()) {
            text.setBold();
        }

        // 斜体
        if (font.getItalic()) {
            text.setItalic();
        }

        // 下划线
        if (font.getUnderline() == 1) {
            text.setUnderline(0.5f, -1f);
        }
    }

    private HSSFPicture getHSSFPicture(HSSFCell cell) {
        HSSFPatriarch patriarch = sheet.getDrawingPatriarch();
        if (patriarch != null) {
            List<HSSFShape> children = patriarch.getChildren();
            for (HSSFShape shape : children) {
                HSSFPicture hssfPicture = (HSSFPicture) shape;
                HSSFClientAnchor clientAnchor = hssfPicture.getClientAnchor();
                if (cell.getRowIndex() == clientAnchor.getRow1() && cell.getColumnIndex() == clientAnchor.getCol1()) {
                    return hssfPicture;
                }
            }
        }
        return null;
    }

    /**
     * 获取PDF纸张和Excel总宽度的比值
     * 用这个值对Excel的大小缩放成pdf需要的大小
     *
     * @return
     */
    private float getRate() {
        float all = 0;
        short lastCellNum = this.sheet.getRow(0).getLastCellNum();
        for (int i = 0; i < lastCellNum; i++) {
            all += this.sheet.getColumnWidth(i);
        }
        PageSize defaultPageSize = null;
        if (document == null) {
            defaultPageSize = PageSize.Default;
        } else {
            defaultPageSize = document.getPdfDocument().getDefaultPageSize();
        }
        this.excelWidth = all;
        float width = defaultPageSize.getWidth();
        return width / all;
    }

    /**
     * 获取单元格列宽
     *
     * @return
     */
    private float[] getColumnWidths() {
        float[] widths = new float[lastCellNum];
        for (int i = 0; i < lastCellNum; i++) {
            int columnWidth = this.sheet.getColumnWidth(i);
            float realWidth = columnWidth * rate;
            widths[i] = realWidth;
        }
        return widths;
    }

    /**
     * 判断单元格是否处理过
     * 合并的单元格只处理第一个
     *
     * @param cell
     * @return
     */
    private boolean isUse(HSSFCell cell) {
        List<CellRangeAddress> mergedRegions = cell.getSheet().getMergedRegions();
        int rowIndex = cell.getRowIndex();
        int columnIndex = cell.getColumnIndex();
        for (CellRangeAddress cellAddresses : mergedRegions) {
            if (cellAddresses.getFirstColumn() <= columnIndex && cellAddresses.getLastColumn() >= columnIndex
                    && cellAddresses.getFirstRow() <= rowIndex && cellAddresses.getLastRow() >= rowIndex
                    && !(cellAddresses.getFirstRow() == rowIndex && cellAddresses.getFirstColumn() == columnIndex)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取合并单元格，只处理第一个
     *
     * @param cell
     * @return
     */
    private CellRangeAddress getCellRangeAddress(HSSFCell cell) {
        List<CellRangeAddress> mergedRegions = cell.getSheet().getMergedRegions();
        int rowIndex = cell.getRowIndex();
        int columnIndex = cell.getColumnIndex();
        for (CellRangeAddress cellAddresses : mergedRegions) {
            if (cellAddresses.getFirstRow() == rowIndex && cellAddresses.getFirstColumn() == columnIndex) {
                return cellAddresses;
            }
        }
        return null;
    }
}
