package com.uc.pptx;

import cn.hutool.core.util.StrUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.TableCell;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.*;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
public class PptxTemplateUtil {

    // 表格默认样式
    private static final Color DEFAULT_HEADER_COLOR = new Color(220, 230, 242);
    private static final Color DEFAULT_BORDER_COLOR = Color.BLACK;
    private static final double DEFAULT_BORDER_WIDTH = 1.0;
    private static final double DEFAULT_ROW_HEIGHT = 30.0;

    private PptxTemplateUtil() {
    }

    /**
     * 核心统一入口：一键处理 PPT 中的文本、图片、表格替换
     *
     * @param sourcePath           模板路径
     * @param destPath             输出路径
     * @param dataMap              普通文本数据源 (Map<占位符, 替换文本>)
     * @param imageMap             图片数据源 (Map<图片占位符, 图片本地路径>)
     * @param tableMap             表格数据源 (Map<表格占位符, 二维数组数据>)
     * @param imagePlaceholderMark 图片占位符标识（例如 "img_" 或 "${img_")
     * @param tablePlaceholderMark 表格占位符标识（例如 "table_" 或 "${table_")
     */
    public static void createPptx(String sourcePath, String destPath, Map<String, String> dataMap, Map<String, String> imageMap,
                                  Map<String, String[][]> tableMap, String imagePlaceholderMark, String tablePlaceholderMark) {
        try (FileInputStream fis = new FileInputStream(sourcePath); XMLSlideShow ppt = new XMLSlideShow(fis)) {
            // 图片数据缓存
            Map<String, XSLFPictureData> pictureCache = new HashMap<>();
            for (XSLFSlide slide : ppt.getSlides()) {
                // 收集当前页需要延迟处理的非文本任务（避免遍历时直接删除 Shape 触发并发修改异常）
                Map<XSLFTextShape, String> imageTasks = new HashMap<>();
                Map<XSLFTextShape, String[][]> tableTasks = new HashMap<>();
                // 深度遍历当前 slide 中的所有文本形状（支持组合形状）
                inspectAndProcessTextShapes(slide, dataMap, imageMap, tableMap, imagePlaceholderMark, tablePlaceholderMark, imageTasks, tableTasks);
                // 统一处理图片替换
                imageTasks.forEach((shape, imgPath) -> replaceShapeWithImage(ppt, slide, shape, imgPath, pictureCache));
                // 统一处理表格替换
                tableTasks.forEach((shape, tableData) -> replaceShapeWithTable(slide, shape, tableData));
            }
            // 写出结果
            try (FileOutputStream out = new FileOutputStream(destPath)) {
                ppt.write(out);
            }
            log.info("PPT 模板处理成功！输出路径: {}", destPath);
        } catch (Exception e) {
            log.error("PPT 处理失败", e);
        }
    }

    /**
     * 递归检查并处理文本形状（兼容顶层 Shape 和 GroupShape 内部的 Shape）
     */
    private static void inspectAndProcessTextShapes(XSLFShapeContainer container, Map<String, String> dataMap, Map<String, String> imageMap,
                                                    Map<String, String[][]> tableMap, String imgMark, String tableMark,
                                                    Map<XSLFTextShape, String> imageTasks, Map<XSLFTextShape, String[][]> tableTasks) {
        for (XSLFShape shape : container.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                XSLFTextShape textShape = (XSLFTextShape) shape;
                String fullText = textShape.getText();
                if (fullText == null || fullText.isEmpty()) {
                    continue;
                }
                // 1. 检查是否匹配【图片】占位符
                if (imageMap != null) {
                    boolean isImageFound = false;
                    for (Map.Entry<String, String> entry : imageMap.entrySet()) {
                        if (StrUtil.containsAnyIgnoreCase(fullText, entry.getKey()) && entry.getKey().contains(imgMark)) {
                            imageTasks.put(textShape, entry.getValue());
                            isImageFound = true;
                            break;
                        }
                    }
                    if (isImageFound) continue; // 如果是图片框，直接跳过，后续不作文本处理
                }

                // 2. 检查是否匹配【表格】占位符
                if (tableMap != null) {
                    boolean isTableFound = false;
                    for (Map.Entry<String, String[][]> entry : tableMap.entrySet()) {
                        if (StrUtil.containsAnyIgnoreCase(fullText, entry.getKey()) && entry.getKey().contains(tableMark)) {
                            tableTasks.put(textShape, entry.getValue());
                            isTableFound = true;
                            break;
                        }
                    }
                    if (isTableFound) continue; // 如果是表格框，直接跳过
                }

                // 3. 检查是否匹配【普通文本】占位符
                if (dataMap != null) {
                    for (Map.Entry<String, String> entry : dataMap.entrySet()) {
                        String key = entry.getKey();
                        if (StrUtil.containsAnyIgnoreCase(fullText, key)) {
                            replaceText(textShape, key, entry.getValue());
                            fullText = textShape.getText(); // 更新文本供下一个 Key 匹配
                        }
                    }
                }

            } else if (shape instanceof XSLFGroupShape) {
                // 如果是组合形状，递归向下遍历
                inspectAndProcessTextShapes((XSLFGroupShape) shape, dataMap, imageMap, tableMap, imgMark, tableMark, imageTasks, tableTasks);
            }
        }
    }

    /**
     * 行为：执行样式保留的普通文字替换
     */
    private static void replaceText(XSLFTextShape shape, String target, String replacement) {
        for (XSLFTextParagraph p : shape.getTextParagraphs()) {
            List<XSLFTextRun> runs = p.getTextRuns();
            if (runs.isEmpty()) continue;

            StringBuilder sb = new StringBuilder();
            for (XSLFTextRun r : runs) {
                sb.append(r.getRawText());
            }
            String content = sb.toString();
            if (content.contains(target)) {
                String result = content.replace(target, replacement);
                runs.get(0).setText(result);
                // 清理多余的 Runs 以维护单 Run 替换稳定性
                for (int i = runs.size() - 1; i > 0; i--) {
                    p.removeTextRun(runs.get(i));
                }
            }
        }
    }

    /**
     * 核心修改：支持本地路径和网络URL的图片替换方法
     */
    private static void replaceShapeWithImage(XMLSlideShow ppt, XSLFSlide slide, XSLFTextShape textShape, String imgPathOrUrl, Map<String, XSLFPictureData> cache) {
        if (StrUtil.isEmpty(imgPathOrUrl)) return;

        XSLFPictureData pd = cache.get(imgPathOrUrl);
        if (pd == null) {
            byte[] pictureBytes = new byte[0];
            // 判断是否为网络图片
            if (imgPathOrUrl.startsWith("http://") || imgPathOrUrl.startsWith("https://")) {
                pictureBytes = downloadNetworkImage(imgPathOrUrl);
            } else {
                // 本地图片
                Path path = Paths.get(imgPathOrUrl);
                if (!Files.exists(path)) {
                    log.warn("本地图片不存在: {}", imgPathOrUrl);
                    return;
                }
                try {
                    pictureBytes = Files.readAllBytes(path);
                } catch (IOException e) {
                    log.error("获取图片失败:{}", path);
                }
            }
            if (pictureBytes == null || pictureBytes.length == 0) {
                log.warn("未能获取到有效的图片数据: {}", imgPathOrUrl);
                return;
            }
            // 自动识别图片类型（简单根据后缀判断，默认PNG）
            PictureData.PictureType type = PictureData.PictureType.PNG;
            String lowerUrl = imgPathOrUrl.toLowerCase();
            if (lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg")) {
                type = PictureData.PictureType.JPEG;
            } else if (lowerUrl.endsWith(".gif")) {
                type = PictureData.PictureType.GIF;
            }
            pd = ppt.addPicture(pictureBytes, type);
            cache.put(imgPathOrUrl, pd);
        }
        Rectangle2D anchor = textShape.getAnchor();
        XSLFPictureShape pictureShape = slide.createPicture(pd);
        pictureShape.setAnchor(anchor);
        removeShapeFromContainer(slide, textShape);
    }

    /**
     * 原生网络图片下载方法（无需依赖额外大框架）
     */
    private static byte[] downloadNetworkImage(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000); // 5秒超时
            conn.setReadTimeout(5000);
            // 伪装浏览器 User-Agent，防止部分防盗链网站拦截
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (InputStream is = conn.getInputStream();
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    return baos.toByteArray();
                }
            } else {
                log.error("网络图片请求失败，响应码: {}, URL: {}", conn.getResponseCode(), urlString);
            }
        } catch (Exception e) {
            log.error("下载网络图片发生异常, URL: {}", urlString, e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }

    /**
     * 行为：将形状替换为表格
     */
    private static void replaceShapeWithTable(XSLFSlide slide, XSLFTextShape textShape, String[][] data) {
        Rectangle2D anchor = textShape.getAnchor();
        if (anchor == null || data == null || data.length == 0) return;
        XSLFTable table = slide.createTable();
        table.setAnchor(anchor);
        for (int i = 0; i < data.length; i++) {
            XSLFTableRow row = table.addRow();
            table.setRowHeight(i, DEFAULT_ROW_HEIGHT);
            for (int j = 0; j < data[i].length; j++) {
                XSLFTableCell cell = row.addCell();
                cell.setText(data[i][j]);
                if (i == 0) {
                    cell.setFillColor(DEFAULT_HEADER_COLOR);
                }
                cell.setVerticalAlignment(VerticalAlignment.MIDDLE);
                if (!cell.getTextParagraphs().isEmpty()) {
                    cell.getTextParagraphs().get(0).setTextAlign(TextParagraph.TextAlign.CENTER);
                }
                setCellBorders(cell, DEFAULT_BORDER_COLOR, DEFAULT_BORDER_WIDTH);
            }
        }
        double totalWidth = anchor.getWidth();
        double colWidth = totalWidth / data[0].length;
        for (int j = 0; j < data[0].length; j++) {
            table.setColumnWidth(j, colWidth);
        }
        removeShapeFromContainer(slide, textShape);
    }

    /**
     * 辅助方法：安全地移除顶层或组合内部的 Shape
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
     * 辅助方法：设置单元格四周全边框
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