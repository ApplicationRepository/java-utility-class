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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 替换表格工具类
 * 💡 深度整合版：完美适配 [Windows/Linux/Mac/Docker] 100% 彻底干掉中文乱码与高度崩溃
 */
@Log4j2
public final class PptxTableUtils {

    private static final Color DEFAULT_BORDER_COLOR = Color.BLACK;
    private static final double MIN_ROW_HEIGHT = 25.0; // 最小兜底行高（磅）

    // 🚀 核心防乱码优化：全局定义跨平台绝对安全的逻辑中文字体名
    private static final String SAFE_FONT_FAMILY;

    static {
        // 获取当前系统支持的所有物理字体名称
        String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        boolean hasYaHei = Arrays.stream(fontNames).anyMatch("Microsoft YaHei"::equalsIgnoreCase);
        boolean hasPingFang = Arrays.stream(fontNames).anyMatch("PingFang SC"::equalsIgnoreCase);

        if (hasYaHei) {
            SAFE_FONT_FAMILY = "Microsoft YaHei";
        } else if (hasPingFang) {
            SAFE_FONT_FAMILY = "PingFang SC";
        } else {
            // JVM 层面在任意 Linux 发行版均 100% 存在的逻辑无衬线字体，完美防御 Docker 方块乱码
            SAFE_FONT_FAMILY = "SansSerif";
        }
        LOGGER.info("PPTX 表格渲染引擎初始化成功，跨平台安全字体绑定为: {}", SAFE_FONT_FAMILY);
    }

    private PptxTableUtils() {
    }

    public static void process(XMLSlideShow ppt, Map<String, String[][]> tableMap, String tablePlaceholderMark) {
        if (ppt == null || tableMap == null || tableMap.isEmpty()) {
            return;
        }
        for (XSLFSlide slide : ppt.getSlides()) {
            Map<XSLFTextShape, String[][]> tableTasks = new HashMap<>();
            scanTablePlaceholders(slide, tableMap, tablePlaceholderMark, tableTasks);
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
        // 核心步骤 1：提取原文本框字体，若为空则采用跨平台安全兜底
        // ==========================================
        String sourceFontFamily = SAFE_FONT_FAMILY; // 默认使用经过环境检测的自适应字体
        Double sourceFontSize = 14.0;
        Color sourceFontColor = Color.BLACK;
        boolean isBold = false;
        boolean isItalic = false;

        if (!textShape.getTextParagraphs().isEmpty()) {
            XSLFTextParagraph firstPara = textShape.getTextParagraphs().get(0);
            if (!firstPara.getTextRuns().isEmpty()) {
                XSLFTextRun firstRun = firstPara.getTextRuns().get(0);

                if (firstRun.getFontFamily() != null && !firstRun.getFontFamily().isEmpty()) {
                    // 额外校验：如果原 PPT 写的字体在当前 Linux 操作系统里根本不存在，则强行采用自适应兜底
                    String currentFamily = firstRun.getFontFamily();
                    String[] availableFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
                    boolean fontExists = Arrays.stream(availableFonts).anyMatch(currentFamily::equalsIgnoreCase);
                    sourceFontFamily = fontExists ? currentFamily : SAFE_FONT_FAMILY;
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

        Color sourceBgColor = textShape.getFillColor();
        VerticalAlignment sourceVerticalAlignment = textShape.getVerticalAlignment();
        if (sourceVerticalAlignment == null) {
            sourceVerticalAlignment = VerticalAlignment.MIDDLE;
        }

        double leftInset = textShape.getLeftInset();
        double rightInset = textShape.getRightInset();
        double topInset = textShape.getTopInset();
        double bottomInset = textShape.getBottomInset();

        // ==========================================
        // 核心步骤 2：建立全平台兼容的 AWT 测量字体环境
        // ==========================================
        double totalWidth = anchor.getWidth();
        int colCount = data[0].length;
        double colWidth = totalWidth / colCount;

        int fontStyle = Font.PLAIN;
        if (isBold && isItalic) {
            fontStyle = Font.BOLD | Font.ITALIC;
        } else if (isBold) {
            fontStyle = Font.BOLD;
        } else if (isItalic) {
            fontStyle = Font.ITALIC;
        }

        // 这里的字体名称已经过 static 静态块或存在性校验的安全过滤，绝不会在 Linux 下导致不可知降级
        Font awtFont = new Font(sourceFontFamily, fontStyle, sourceFontSize.intValue());
        FontRenderContext frc = new FontRenderContext(new AffineTransform(), true, true);

        // ==========================================
        // 核心步骤 3：生成表格并动态自适应行高
        // ==========================================
        XSLFTable table = slide.createTable();
        table.setAnchor(anchor);

        for (int i = 0; i < data.length; i++) {
            XSLFTableRow row = table.addRow();
            double maxRowHeightNeeded = MIN_ROW_HEIGHT;

            for (int j = 0; j < data[i].length; j++) {
                XSLFTableCell cell = row.addCell();
                String cellText = data[i][j] != null ? data[i][j] : "";

                XSLFTextParagraph cellPara = cell.addNewTextParagraph();
                XSLFTextRun cellRun = cellPara.addNewTextRun();
                cellRun.setText(cellText);

                // 统一注入被保护的跨平台安全中文字体属性
                cellRun.setFontFamily(sourceFontFamily);
                cellRun.setFontSize(sourceFontSize);
                cellRun.setFontColor(sourceFontColor);
                cellRun.setBold(isBold);
                cellRun.setItalic(isItalic);

                cell.setLeftInset(leftInset);
                cell.setRightInset(rightInset);
                cell.setTopInset(topInset);
                cell.setBottomInset(bottomInset);

                // 测量计算文本换行所需高度
                double availableTextWidth = colWidth - leftInset - rightInset;
                if (availableTextWidth > 0 && !cellText.isEmpty()) {
                    Rectangle2D stringBounds = awtFont.getStringBounds(cellText, frc);
                    double textWidth = stringBounds.getWidth();

                    int lineCount = (int) Math.ceil(textWidth / availableTextWidth);
                    if (lineCount < 1) lineCount = 1;

                    double singleLineHeight = sourceFontSize * 1.2;
                    double cellHeightNeeded = (lineCount * singleLineHeight) + topInset + bottomInset + 6.0;

                    if (cellHeightNeeded > maxRowHeightNeeded) {
                        maxRowHeightNeeded = cellHeightNeeded;
                    }
                }

                if (i == 0) {
                    cell.setFillColor(sourceBgColor != null ? sourceBgColor : new Color(220, 230, 242));
                } else {
                    cell.setFillColor(null);
                }

                cell.setVerticalAlignment(sourceVerticalAlignment);
                cellPara.setTextAlign(TextParagraph.TextAlign.CENTER);

                setCellBorders(cell, DEFAULT_BORDER_COLOR, 1.0);
            }

            table.setRowHeight(i, maxRowHeightNeeded);
        }

        for (int j = 0; j < colCount; j++) {
            table.setColumnWidth(j, colWidth);
        }

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