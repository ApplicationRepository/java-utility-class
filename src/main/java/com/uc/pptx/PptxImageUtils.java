package com.uc.pptx;

import cn.hutool.core.util.StrUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 替换图片工具类（强制 100% 铺满拉伸版 - 重构优化版）
 */
@Log4j2
public final class PptxImageUtils {

    private PptxImageUtils() {
    }

    /**
     * 一键图片占位符替换入口
     */
    public static void process(XMLSlideShow ppt, Map<String, String> imageMap, String imagePlaceholderMark) {
        if (ppt == null || imageMap == null || imageMap.isEmpty()) {
            return;
        }
        Map<String, XSLFPictureData> pictureCache = new HashMap<>();
        for (XSLFSlide slide : ppt.getSlides()) {
            Map<XSLFTextShape, String> imageTasks = new HashMap<>();

            // 深度扫描所有层级组件
            scanImagePlaceholders(slide, imageMap, imagePlaceholderMark, imageTasks);

            // 统一无损渲染
            imageTasks.forEach((shape, imgPathOrUrl) -> {
                try {
                    replaceShapeWithImage(ppt, slide, shape, imgPathOrUrl, pictureCache);
                } catch (Exception e) {
                    LOGGER.error("图片替换失败, 目标源: {}", imgPathOrUrl, e);
                }
            });
        }
        LOGGER.info("图片替换生成完毕");
    }

    /**
     * 深度扫描引擎
     */
    private static void scanImagePlaceholders(XSLFShapeContainer container, Map<String, String> imageMap, String imgMark, Map<XSLFTextShape, String> imageTasks) {
        for (XSLFShape shape : container.getShapes()) {
            if (shape instanceof XSLFTableCell) {
                checkAndAddTask((XSLFTableCell) shape, ((XSLFTableCell) shape).getText(), imageMap, imgMark, imageTasks);
            } else if (shape instanceof XSLFTextShape) {
                checkAndAddTask((XSLFTextShape) shape, ((XSLFTextShape) shape).getText(), imageMap, imgMark, imageTasks);
            } else if (shape instanceof XSLFGroupShape) {
                scanImagePlaceholders((XSLFGroupShape) shape, imageMap, imgMark, imageTasks);
            } else if (shape instanceof XSLFTable) {
                XSLFTable table = (XSLFTable) shape;
                for (XSLFTableRow row : table.getRows()) {
                    for (XSLFTableCell cell : row.getCells()) {
                        checkAndAddTask(cell, cell.getText(), imageMap, imgMark, imageTasks);
                    }
                }
            }
        }
    }

    /**
     * 提取出的公共匹配与提取任务方法
     */
    private static void checkAndAddTask(XSLFTextShape shape, String text, Map<String, String> imageMap, String imgMark, Map<XSLFTextShape, String> imageTasks) {
        if (StrUtil.isEmpty(text)) {
            return;
        }
        for (Map.Entry<String, String> entry : imageMap.entrySet()) {
            if (StrUtil.containsAnyIgnoreCase(text, entry.getKey()) && entry.getKey().contains(imgMark)) {
                imageTasks.put(shape, entry.getValue());
                break;
            }
        }
    }

    /**
     * 核心替换逻辑
     */
    private static void replaceShapeWithImage(XMLSlideShow ppt, XSLFSlide slide, XSLFTextShape textShape, String imgPathOrUrl, Map<String, XSLFPictureData> cache) throws IOException {
        if (StrUtil.isEmpty(imgPathOrUrl)) {
            return;
        }

        XSLFPictureData pd = cache.get(imgPathOrUrl);
        if (pd == null) {
            // 提取公共的数据流获取逻辑
            byte[] pictureBytes = fetchImageBytes(imgPathOrUrl);
            if (pictureBytes == null) {
                return;
            }
            PictureData.PictureType type = detectPictureType(pictureBytes, imgPathOrUrl);
            pd = ppt.addPicture(pictureBytes, type);
            cache.put(imgPathOrUrl, pd);
        }

        java.awt.geom.Rectangle2D.Double anchor = (java.awt.geom.Rectangle2D.Double) textShape.getAnchor();
        if (anchor == null) {
            return;
        }

        // 创建图片对象并强制 100% 铺满拉伸
        XSLFPictureShape pictureShape = slide.createPicture(pd);
        pictureShape.setAnchor(anchor);

        // 安全清理原文本组件
        if (textShape instanceof XSLFTableCell) {
            textShape.setText("");
        } else {
            removeShape(slide, textShape);
        }
    }

    /**
     * 提取出的公共数据流获取方法
     */
    private static byte[] fetchImageBytes(String imgPathOrUrl) throws IOException {
        return imgPathOrUrl.startsWith("http") ? downloadNetworkImage(imgPathOrUrl) : readLocalImage(imgPathOrUrl);
    }

    /**
     * 智能图片类型探测器（基于文件流特征魔数检测）
     */
    private static PictureData.PictureType detectPictureType(byte[] bytes, String fallbackPath) {
        if (bytes.length > 4) {
            if (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47) {
                return PictureData.PictureType.PNG;
            }
            if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
                return PictureData.PictureType.JPEG;
            }
            if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
                return PictureData.PictureType.GIF;
            }
        }
        String pathLower = fallbackPath.toLowerCase();
        if (pathLower.contains(".jpg") || pathLower.contains(".jpeg")) {
            return PictureData.PictureType.JPEG;
        } else if (pathLower.contains(".gif")) {
            return PictureData.PictureType.GIF;
        }
        return PictureData.PictureType.PNG;
    }

    private static byte[] readLocalImage(String pathStr) throws IOException {
        Path path = Paths.get(pathStr);
        return Files.exists(path) ? Files.readAllBytes(path) : null;
    }

    private static byte[] downloadNetworkImage(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (InputStream is = conn.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[2048];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    return baos.toByteArray();
                }
            }
        } catch (Exception e) {
            LOGGER.error("远程图片下载失败: {}", urlString, e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
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
}