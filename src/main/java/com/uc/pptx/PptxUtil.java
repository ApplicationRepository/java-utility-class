package com.uc.pptx;

import cn.hutool.core.util.StrUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.*;

import java.awt.geom.Rectangle2D;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Log4j2
public class PptxUtil {

    private PptxUtil() {
    }


    public static void createPptx(String sourcePath, String destPath, Map<String, String> dataMap, String imagePlaceholderMark) {
        try (FileInputStream fis = new FileInputStream(sourcePath);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
            // 图片数据缓存：避免同一张图片在 PPT 中多次重复保存，优化文件大小
            Map<String, XSLFPictureData> pictureCache = new HashMap<>();
            for (XSLFSlide slide : ppt.getSlides()) {
                // 暂存本页需要替换为图片的形状，避免在遍历时删除导致并发修改异常
                Map<XSLFTextShape, String> imageTasks = new HashMap<>();
                // 遍历幻灯片中的所有形状
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        XSLFTextShape textShape = (XSLFTextShape) shape;
                        String fullText = textShape.getText();
                        if (fullText == null || fullText.isEmpty()) {
                            continue;
                        }
                        // 检查该文本框是否匹配 dataMap 中的任何占位符
                        for (Map.Entry<String, String> entry : dataMap.entrySet()) {
                            String key = entry.getKey();
                            String value = entry.getValue();
                            log.info("key:{},value:{}", key, value);
                            if (StrUtil.containsAnyIgnoreCase(fullText, key)) {
                                if (key.startsWith(imagePlaceholderMark)) {
                                    // 识别为图片占位符：记录任务并跳出，不再进行文字替换
                                    imageTasks.put(textShape, value);
                                    break;
                                } else {
                                    // 识别为普通文本：执行样式保留的文字替换
                                    replaceText(textShape, key, value);
                                    // 更新 fullText 供后续 Key 继续匹配（一个框内可能有多个变量）
                                    fullText = textShape.getText();
                                }
                            }
                        }
                    }
                }
                // 统一处理图片替换
                imageTasks.forEach((shape, imgPath) -> {
                    try {
                        replaceShapeWithImage(ppt, slide, shape, imgPath, pictureCache);
                    } catch (IOException e) {
                        log.error("图片替换失败:{}", imgPath);
                    }
                });
            }
            // 写出结果
            try (FileOutputStream out = new FileOutputStream(destPath)) {
                ppt.write(out);
            }
            log.info("处理成功！输出路径:{}", destPath);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("处理失败:{}", e.toString());
        }
    }

    private static void replaceText(XSLFTextShape shape, String target, String replacement) {
        for (XSLFTextParagraph p : shape.getTextParagraphs()) {
            List<XSLFTextRun> runs = p.getTextRuns();
            if (runs.isEmpty()) {
                continue;
            }
            // 获取段落完整文字
            StringBuilder sb = new StringBuilder();
            for (XSLFTextRun r : runs) {
                sb.append(r.getRawText());
            }
            String content = sb.toString();
            log.info("content:{}", content);
            if (content.contains(target)) {
                String result = content.replace(target, replacement);
                // 将新文字写回第一个 Run，删除后续 Run 以保持格式并清理旧内容
                runs.get(0).setText(result);
                for (int i = runs.size() - 1; i > 0; i--) {
                    p.removeTextRun(runs.get(i));
                }
            }
        }
    }

    private static void replaceShapeWithImage(XMLSlideShow ppt, XSLFSlide slide, XSLFTextShape textShape, String imgPath, Map<String, XSLFPictureData> cache) throws IOException {
        Path path = Paths.get(imgPath);
        if (!Files.exists(path)) {
            return;
        }
        // 缓存机制：确保同一张图片数据在 PPT 内只存一份
        XSLFPictureData pd = cache.get(imgPath);
        if (pd == null) {
            byte[] pictureBytes = Files.readAllBytes(path);
            pd = ppt.addPicture(pictureBytes, PictureData.PictureType.PNG);
            cache.put(imgPath, pd);
        }
        // 创建图片并继承原文本框的位置、大小
        Rectangle2D anchor = textShape.getAnchor();
        XSLFPictureShape pictureShape = slide.createPicture(pd);
        pictureShape.setAnchor(anchor);
        // 从幻灯片中移除原有的占位符文本框
        slide.removeShape(textShape);
    }
}