package com.uc.pptx;

import cn.hutool.core.util.StrUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.*;

import java.awt.geom.Rectangle2D;
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

@Log4j2
public final class PptxImageUtils {

    private PptxImageUtils() {
    }

    public static void process(XMLSlideShow ppt, Map<String, String> imageMap, String imagePlaceholderMark) {
        if (ppt == null || imageMap == null || imageMap.isEmpty()) {
            return;
        }
        Map<String, XSLFPictureData> pictureCache = new HashMap<>();
        for (XSLFSlide slide : ppt.getSlides()) {
            Map<XSLFTextShape, String> imageTasks = new HashMap<>();
            // 扫描占位符
            scanImagePlaceholders(slide, imageMap, imagePlaceholderMark, imageTasks);
            // 统一渲染
            imageTasks.forEach((shape, imgPathOrUrl) -> {
                try {
                    replaceShapeWithImage(ppt, slide, shape, imgPathOrUrl, pictureCache);
                } catch (Exception e) {
                    LOGGER.error("图片替换失败: {}", imgPathOrUrl, e);
                }
            });
        }
    }

    private static void scanImagePlaceholders(XSLFShapeContainer container, Map<String, String> imageMap, String imgMark, Map<XSLFTextShape, String> imageTasks) {
        for (XSLFShape shape : container.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                XSLFTextShape textShape = (XSLFTextShape) shape;
                String fullText = textShape.getText();
                if (fullText == null || fullText.isEmpty()) {
                    continue;
                }
                for (Map.Entry<String, String> entry : imageMap.entrySet()) {
                    if (StrUtil.containsAnyIgnoreCase(fullText, entry.getKey()) && entry.getKey().contains(imgMark)) {
                        imageTasks.put(textShape, entry.getValue());
                        break;
                    }
                }
            } else if (shape instanceof XSLFGroupShape) {
                scanImagePlaceholders((XSLFGroupShape) shape, imageMap, imgMark, imageTasks);
            }
        }
    }

    private static void replaceShapeWithImage(XMLSlideShow ppt, XSLFSlide slide, XSLFTextShape textShape, String imgPathOrUrl, Map<String, XSLFPictureData> cache) throws IOException {
        if (StrUtil.isEmpty(imgPathOrUrl)) {
            return;
        }
        XSLFPictureData pd = cache.get(imgPathOrUrl);
        if (pd == null) {
            byte[] pictureBytes = imgPathOrUrl.startsWith("http") ? downloadNetworkImage(imgPathOrUrl) : readLocalImage(imgPathOrUrl);
            if (pictureBytes == null) {
                return;
            }
            PictureData.PictureType type = imgPathOrUrl.toLowerCase().endsWith(".jpg") || imgPathOrUrl.toLowerCase().endsWith(".jpeg") ? PictureData.PictureType.JPEG : PictureData.PictureType.PNG;
            pd = ppt.addPicture(pictureBytes, type);
            cache.put(imgPathOrUrl, pd);
        }
        Rectangle2D anchor = textShape.getAnchor();
        XSLFPictureShape pictureShape = slide.createPicture(pd);
        pictureShape.setAnchor(anchor);
        removeShape(slide, textShape);
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
            conn.setConnectTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (InputStream is = conn.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = is.read(buffer)) != -1) baos.write(buffer, 0, len);
                    return baos.toByteArray();
                }
            }
        } catch (Exception e) {
            LOGGER.error("下载网络图片失败: {}", urlString, e);
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
            if (shape instanceof XSLFGroupShape) removeShape((XSLFGroupShape) shape, target);
        }
    }
}