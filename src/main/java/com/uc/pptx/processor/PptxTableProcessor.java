package com.uc.pptx.processor;

import cn.hutool.core.util.StrUtil;
import org.apache.poi.sl.usermodel.TableCell;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.*;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;

/**
 * 专门负责将占位符文本框替换为新生成的表格
 */
public class PptxTableProcessor {

    private static final Color DEFAULT_HEADER_COLOR = new Color(220, 230, 242);
    private static final Color DEFAULT_BORDER_COLOR = Color.BLACK;

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
        XSLFTable table = slide.createTable();
        table.setAnchor(anchor);

        for (int i = 0; i < data.length; i++) {
            XSLFTableRow row = table.addRow();
            table.setRowHeight(i, 30.0);

            for (int j = 0; j < data[i].length; j++) {
                XSLFTableCell cell = row.addCell();
                cell.setText(data[i][j]);

                if (i == 0) cell.setFillColor(DEFAULT_HEADER_COLOR);

                cell.setVerticalAlignment(VerticalAlignment.MIDDLE);
                if (!cell.getTextParagraphs().isEmpty()) {
                    cell.getTextParagraphs().get(0).setTextAlign(TextParagraph.TextAlign.CENTER);
                }
                setCellBorders(cell, DEFAULT_BORDER_COLOR, 1.0);
            }
        }

        double totalWidth = anchor.getWidth();
        double colWidth = totalWidth / data[0].length;
        for (int j = 0; j < data[0].length; j++) {
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