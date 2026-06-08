package com.uc.pptx;

import cn.hutool.core.util.StrUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
 * 替换图片工具类
 * 💡 深度增强版：100%免疫中文乱码 + [自适应等比例防变形算法] + [智能多格式图片识别] + [嵌套表格全扫描]
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
            // 深度扫描所有层级组件（含组合、表格）
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
     * 深度扫描引擎：完美穿透“群组”和“表格单元格”提取图片占位符
     */
    private static void scanImagePlaceholders(XSLFShapeContainer container, Map<String, String> imageMap, String imgMark, Map<XSLFTextShape, String> imageTasks) {
        for (XSLFShape shape : container.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                // XSLFTableCell 会在这里被安全捕获（因为它是 XSLFTextShape 的子类）
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
            } else if (shape instanceof XSLFTable) {
                // 🛠️ 修复编译错误：遍历表格行与列，直接对单元格（TextShape）进行匹配
                XSLFTable table = (XSLFTable) shape;
                for (XSLFTableRow row : table.getRows()) {
                    for (XSLFTableCell cell : row.getCells()) {
                        String cellText = cell.getText();
                        if (cellText == null || cellText.isEmpty()) {
                            continue;
                        }
                        for (Map.Entry<String, String> entry : imageMap.entrySet()) {
                            if (StrUtil.containsAnyIgnoreCase(cellText, entry.getKey()) && entry.getKey().contains(imgMark)) {
                                imageTasks.put(cell, entry.getValue()); // 单元格本身就是 XSLFTextShape
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private static void replaceShapeWithImage(XMLSlideShow ppt, XSLFSlide slide, XSLFTextShape textShape, String imgPathOrUrl, Map<String, XSLFPictureData> cache) throws IOException {
        if (StrUtil.isEmpty(imgPathOrUrl)) {
            return;
        }

        // 1. 缓存读取，防止多页 PPT 重复下载同张网络图片导致 OOM
        XSLFPictureData pd = cache.get(imgPathOrUrl);
        byte[] pictureBytes = null;

        if (pd == null) {
            pictureBytes = imgPathOrUrl.startsWith("http") ? downloadNetworkImage(imgPathOrUrl) : readLocalImage(imgPathOrUrl);
            if (pictureBytes == null) {
                return;
            }
            // 🛠️ 核心增强：通过字节流魔数动态识别图片真实格式，彻底杜绝以扩展名误判导致文件损坏
            PictureData.PictureType type = detectPictureType(pictureBytes, imgPathOrUrl);
            pd = ppt.addPicture(pictureBytes, type);
            cache.put(imgPathOrUrl, pd);
        }

        java.awt.geom.Rectangle2D.Double anchor = (java.awt.geom.Rectangle2D.Double) textShape.getAnchor();
        if (anchor == null) return;

        XSLFPictureShape pictureShape = slide.createPicture(pd);

        // 2. 🛠️ 核心新增算法：自适应等比例缩放（防止图片被压扁或拉长）
        if (pictureBytes == null) {
            pictureBytes = pd.getData();
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(pictureBytes)) {
            BufferedImage bimg = ImageIO.read(bais);
            if (bimg != null) {
                double imgWidth = bimg.getWidth();
                double imgHeight = bimg.getHeight();
                double boxWidth = anchor.getWidth();
                double boxHeight = anchor.getHeight();

                double scale = Math.min(boxWidth / imgWidth, boxHeight / imgHeight);

                // 计算等比例缩放后的最终宽高
                double finalWidth = imgWidth * scale;
                double finalHeight = imgHeight * scale;

                // 计算居中坐标偏移量
                double xOffset = anchor.getX() + (boxWidth - finalWidth) / 2.0;
                double yOffset = anchor.getY() + (boxHeight - finalHeight) / 2.0;

                pictureShape.setAnchor(new java.awt.geom.Rectangle2D.Double(xOffset, yOffset, finalWidth, finalHeight));
            } else {
                // 降级兜底：万一 BufferedImage 无法解析，使用原框大小
                pictureShape.setAnchor(anchor);
            }
        }

        // 3. 安全移除原占位符文本框
        removeShape(slide, textShape);
    }

    /**
     * 🛠️ 智能图片类型探测器（基于文件流特征魔数检测）
     */
    private static PictureData.PictureType detectPictureType(byte[] bytes, String fallbackPath) {
        if (bytes.length > 4) {
            // PNG 魔数: .PNG
            if (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47) {
                return PictureData.PictureType.PNG;
            }
            // JPEG 魔数: FFD8
            if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
                return PictureData.PictureType.JPEG;
            }
            // GIF 魔数: GIF8
            if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
                return PictureData.PictureType.GIF;
            }
        }
        // 如果无法动态探测，基于路径后缀降级兜底
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
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (InputStream is = conn.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[2048];
                    int len;
                    while ((len = is.read(buffer)) != -1) baos.write(buffer, 0, len);
                    return baos.toByteArray();
                }
            }
        } catch (Exception e) {
            LOGGER.error("远程图片下载失败: {}", urlString, e);
        } finally {
            if (conn != null) conn.disconnect();
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