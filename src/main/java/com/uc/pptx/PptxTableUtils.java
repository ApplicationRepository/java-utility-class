package com.uc.pptx;

import cn.hutool.core.util.StrUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.sl.usermodel.PaintStyle;
import org.apache.poi.sl.usermodel.TableCell;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.*;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;

/**
 * 替换表格
 */
@Log4j2
public final class PptxTableUtils {

    private static final Color DEFAULT_BORDER_COLOR = Color.BLACK;
    private static final double MIN_ROW_HEIGHT = 25.0; // 设定一个最小兜底行高（磅）

    private PptxTableUtils() {
    }

    public static void process(XMLSlideShow ppt, Map<String, String[][]> tableMap, String tablePlaceholderMark) {
        if (ppt == null || tableMap == null || tableMap.isEmpty()) {
            return;
        }
        for (XSLFSlide slide : ppt.getSlides()) {
            Map<XSLFTextShape, String[][]> tableTasks = new HashMap<>();
            // 扫描占位符
            scanTablePlaceholders(slide, tableMap, tablePlaceholderMark, tableTasks);
            // 统一渲染
            tableTasks.forEach((shape, tableData) -> replaceShapeWithTable(slide, shape, tableData));
        }
        LOGGER.info("表格生成完毕");
    }

    private static void scanTablePlaceholders(XSLFShapeContainer container, Map<String, String[][]> tableMap, String tableMark, Map<XSLFTextShape, String[][]> tableTasks) {
        for (XSLFShape shape : container.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                XSLFTextShape textShape = (XSLFTextShape) shape;
                String fullText = textShape.getText();
                if (fullText == null || fullText.isEmpty()) {
                    continue;
                }
                for (Map.Entry<String, String[][]> entry : tableMap.entrySet()) {
                    if (StrUtil.containsAnyIgnoreCase(fullText, entry.getKey()) && entry.getKey().contains(tableMark)) {
                        tableTasks.put(textShape, entry.getValue());
                        break;
                    }
                }
            } else if (shape instanceof XSLFGroupShape) {
                scanTablePlaceholders((XSLFGroupShape) shape, tableMap, tableMark, tableTasks);
            }
        }
    }

    private static void replaceShapeWithTable(XSLFSlide slide, XSLFTextShape textShape, String[][] data) {
        Rectangle2D anchor = textShape.getAnchor();
        if (anchor == null || data == null || data.length == 0) {
            return;
        }
        // ==========================================
        // 核心步骤 1：提取原文本框的精细字体样式
        // ==========================================
        String sourceFontFamily = "微软雅黑";   // 默认兜底字体
        Double sourceFontSize = 14.0;         // 默认兜底字号
        Color sourceFontColor = Color.BLACK;   // 默认兜底颜色
        boolean isBold = false;
        boolean isItalic = false;
        if (!textShape.getTextParagraphs().isEmpty()) {
            XSLFTextParagraph firstPara = textShape.getTextParagraphs().get(0);
            if (!firstPara.getTextRuns().isEmpty()) {
                XSLFTextRun firstRun = firstPara.getTextRuns().get(0);

                if (firstRun.getFontFamily() != null) {
                    sourceFontFamily = firstRun.getFontFamily();
                }
                if (firstRun.getFontSize() != null) {
                    sourceFontSize = firstRun.getFontSize();
                }
                isBold = firstRun.isBold();
                isItalic = firstRun.isItalic();

                PaintStyle paint = firstRun.getFontColor();
                if (paint instanceof PaintStyle.SolidPaint) {
                    PaintStyle.SolidPaint solidPaint = (PaintStyle.SolidPaint) paint;
                    sourceFontColor = solidPaint.getSolidColor().getColor();
                }
            }
        }

        // 提取原文本框的背景填充色与垂直对齐方式
        Color sourceBgColor = textShape.getFillColor();
        VerticalAlignment sourceVerticalAlignment = textShape.getVerticalAlignment();
        if (sourceVerticalAlignment == null) {
            sourceVerticalAlignment = VerticalAlignment.MIDDLE;
        }

        // 提取原文本框的四周内边距
        double leftInset = textShape.getLeftInset();
        double rightInset = textShape.getRightInset();
        double topInset = textShape.getTopInset();
        double bottomInset = textShape.getBottomInset();

        // ==========================================
        // 核心步骤 2：预先计算分配列宽
        // ==========================================
        double totalWidth = anchor.getWidth();
        int colCount = data[0].length;
        double colWidth = totalWidth / colCount;

        // 构建 AWT 字体环境，用来精确测量模拟文字所占宽度
        int fontStyle = Font.PLAIN;
        if (isBold && isItalic) {
            fontStyle = Font.BOLD | Font.ITALIC;
        } else if (isBold) {
            fontStyle = Font.BOLD;
        } else if (isItalic) {
            fontStyle = Font.ITALIC;
        }
        Font awtFont = new Font(sourceFontFamily, fontStyle, sourceFontSize.intValue());
        FontRenderContext frc = new FontRenderContext(new AffineTransform(), true, true);

        // ==========================================
        // 核心步骤 3：生成表格并动态自适应行高
        // ==========================================
        XSLFTable table = slide.createTable();
        table.setAnchor(anchor);

        for (int i = 0; i < data.length; i++) {
            XSLFTableRow row = table.addRow();

            // 核心变更：动态计算当前行所需的最高值
            double maxRowHeightNeeded = MIN_ROW_HEIGHT;

            for (int j = 0; j < data[i].length; j++) {
                XSLFTableCell cell = row.addCell();
                String cellText = data[i][j] != null ? data[i][j] : "";

                XSLFTextParagraph cellPara = cell.addNewTextParagraph();
                XSLFTextRun cellRun = cellPara.addNewTextRun();
                cellRun.setText(cellText);

                // 1. 注入原文本框字体属性
                cellRun.setFontFamily(sourceFontFamily);
                cellRun.setFontSize(sourceFontSize);
                cellRun.setFontColor(sourceFontColor);
                cellRun.setBold(isBold);
                cellRun.setItalic(isItalic);

                // 2. 注入原文本框的边距属性
                cell.setLeftInset(leftInset);
                cell.setRightInset(rightInset);
                cell.setTopInset(topInset);
                cell.setBottomInset(bottomInset);

                // 3. 🛠️【核心新增算法】计算该单元格由于文字换行所需的理想高度
                double availableTextWidth = colWidth - leftInset - rightInset; // 减去内边距后真正可容纳文字的宽度
                if (availableTextWidth > 0 && !cellText.isEmpty()) {
                    // 测量当前文本如果不换行的总像素宽度
                    Rectangle2D stringBounds = awtFont.getStringBounds(cellText, frc);
                    double textWidth = stringBounds.getWidth();

                    // 估算行数 (向上取整)
                    int lineCount = (int) Math.ceil(textWidth / availableTextWidth);
                    if (lineCount < 1) lineCount = 1;

                    // 估算单行文本的高度 (字号大小 * 基础行距系数，通常为1.2)
                    double singleLineHeight = sourceFontSize * 1.2;

                    // 单元格总需要高度 = (行数 * 单行高) + 上边距 + 下边距 + 缓冲安全距离
                    double cellHeightNeeded = (lineCount * singleLineHeight) + topInset + bottomInset + 6.0;

                    // 这一行的高度取决于这一行里“最高”的那个单元格
                    if (cellHeightNeeded > maxRowHeightNeeded) {
                        maxRowHeightNeeded = cellHeightNeeded;
                    }
                }

                // 4. 继承原文本框的背景色逻辑
                if (i == 0) {
                    cell.setFillColor(sourceBgColor != null ? sourceBgColor : new Color(220, 230, 242));
                } else {
                    cell.setFillColor(null);
                }

                // 5. 继承垂直对齐方式，水平居中对齐
                cell.setVerticalAlignment(sourceVerticalAlignment);
                cellPara.setTextAlign(TextParagraph.TextAlign.CENTER);

                setCellBorders(cell, DEFAULT_BORDER_COLOR, 1.0);
            }

            // 🛠️ 动态设置计算出来的行高
            table.setRowHeight(i, maxRowHeightNeeded);
        }

        // 统一设置列宽
        for (int j = 0; j < colCount; j++) {
            table.setColumnWidth(j, colWidth);
        }

        // 移除原文本框占位符
        removeShape(slide, textShape);
    }

    private static void removeShape(XSLFShapeContainer container, XSLFShape target) {
        if (container.getShapes().contains(target)) {
            container.removeShape(target);
            return;
        }
        for (XSLFShape shape : container.getShapes()) {
            if (shape instanceof XSLFGroupShape) {
                removeShape((XSLFGroupShape) shape, target);
            }
        }
    }

    private static void setCellBorders(XSLFTableCell cell, Color color, double width) {
        for (TableCell.BorderEdge edge : TableCell.BorderEdge.values()) {
            cell.setBorderColor(edge, color);
            cell.setBorderWidth(edge, width);
        }
    }
}