package com.uc.pptx;

import org.apache.poi.sl.usermodel.TableCell;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.*;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * PPT 占位符替换为表格通用工具类
 */
public class PptxTableUtil {

    // 默认样式常量
    private static final Color DEFAULT_HEADER_COLOR = new Color(220, 230, 242); // 默认浅蓝表头
    private static final Color DEFAULT_BORDER_COLOR = Color.BLACK;              // 默认黑色边框
    private static final double DEFAULT_BORDER_WIDTH = 1.0;                     // 默认 1.0 磅
    private static final double DEFAULT_ROW_HEIGHT = 30.0;                      // 默认行高 30

    private PptxTableUtil() {
    }

    /**
     * 核心方法：使用默认样式将 PPT 中的文本占位符替换为表格
     *
     * @param ppt         XMLSlideShow 对象
     * @param placeholder 占位符字符串，例如 "${myTable}"
     * @param data        二维数组数据
     */
    public static void replacePlaceholderWithTable(XMLSlideShow ppt, String placeholder, String[][] data) {
        replacePlaceholderWithTable(ppt, placeholder, data, DEFAULT_HEADER_COLOR, DEFAULT_BORDER_COLOR, DEFAULT_BORDER_WIDTH, DEFAULT_ROW_HEIGHT);
    }

    /**
     * 核心方法：自定义样式将 PPT 中的文本占位符替换为表格
     *
     * @param ppt         XMLSlideShow 对象
     * @param placeholder 占位符字符串
     * @param data        二维数组数据
     * @param headerColor 表头背景色（传入 null 则不设置背景色）
     * @param borderColor 边框颜色（传入 null 则无边框）
     * @param borderWidth 边框粗细
     * @param rowHeight   行高
     */
    public static void replacePlaceholderWithTable(XMLSlideShow ppt, String placeholder, String[][] data,
                                                   Color headerColor, Color borderColor, double borderWidth, double rowHeight) {
        if (ppt == null || placeholder == null || data == null || data.length == 0) {
            return;
        }

        // 遍历所有幻灯片
        for (XSLFSlide slide : ppt.getSlides()) {
            XSLFTextShape targetShape = findTargetShape(slide, placeholder);
            if (targetShape != null) {
                Rectangle2D anchor = targetShape.getAnchor();
                if (anchor == null) continue;
                // 1. 创建表格并继承原占位符位置
                XSLFTable table = slide.createTable();
                table.setAnchor(anchor);
                // 2. 填充数据与基础样式
                for (int i = 0; i < data.length; i++) {
                    XSLFTableRow row = table.addRow();
                    table.setRowHeight(i, rowHeight);
                    for (int j = 0; j < data[i].length; j++) {
                        XSLFTableCell cell = row.addCell();
                        cell.setText(data[i][j]);
                        // 表头背景色
                        if (i == 0 && headerColor != null) {
                            cell.setFillColor(headerColor);
                        }
                        // 居中对齐
                        cell.setVerticalAlignment(VerticalAlignment.MIDDLE);
                        if (!cell.getTextParagraphs().isEmpty()) {
                            cell.getTextParagraphs().get(0).setTextAlign(TextParagraph.TextAlign.CENTER);
                        }
                        // 设置边框
                        if (borderColor != null) {
                            setCellBorders(cell, borderColor, borderWidth);
                        }
                    }
                }
                // 3. 计算并设置列宽（平均分配）
                double totalWidth = anchor.getWidth();
                double colWidth = totalWidth / data[0].length;
                for (int j = 0; j < data[0].length; j++) {
                    table.setColumnWidth(j, colWidth);
                }
                // 4. 从当前幻灯片中移除原有的文本框（支持组合形状内的移除）
                removeShapeFromContainer(slide, targetShape);
            }
        }
    }

    /**
     * 递归查找幻灯片中的目标文本形状（支持组合形状 GroupShape）
     */
    private static XSLFTextShape findTargetShape(XSLFShapeContainer container, String placeholder) {
        for (XSLFShape shape : container.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                XSLFTextShape textShape = (XSLFTextShape) shape;
                String text = textShape.getText();
                if (text != null && text.contains(placeholder)) {
                    return textShape;
                }
            } else if (shape instanceof XSLFGroupShape) {
                // 如果是组合形状，递归向下查找
                XSLFTextShape result = findTargetShape((XSLFGroupShape) shape, placeholder);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * 安全地移除 Shape（考虑到了 GroupShape 内部的情况）
     */
    private static void removeShapeFromContainer(XSLFShapeContainer container, XSLFShape target) {
        if (container.getShapes().contains(target)) {
            container.removeShape(target);
            return;
        }
        for (XSLFShape shape : container.getShapes()) {
            if (shape instanceof XSLFGroupShape) {
                removeShapeFromContainer((XSLFGroupShape) shape, target);
            }
        }
    }

    /**
     * 设置单元格四周全边框
     */
    private static void setCellBorders(XSLFTableCell cell, Color color, double width) {
        cell.setBorderColor(TableCell.BorderEdge.top, color);
        cell.setBorderWidth(TableCell.BorderEdge.top, width);
        cell.setBorderColor(TableCell.BorderEdge.bottom, color);
        cell.setBorderWidth(TableCell.BorderEdge.bottom, width);
        cell.setBorderColor(TableCell.BorderEdge.left, color);
        cell.setBorderWidth(TableCell.BorderEdge.left, width);
        cell.setBorderColor(TableCell.BorderEdge.right, color);
        cell.setBorderWidth(TableCell.BorderEdge.right, width);
    }
}