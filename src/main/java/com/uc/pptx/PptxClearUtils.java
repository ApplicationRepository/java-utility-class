package com.uc.pptx;

import lombok.extern.log4j.Log4j2;
import org.apache.poi.xslf.usermodel.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 清理未被替换的占位符以及空白文本框
 */
@Log4j2
public final class PptxClearUtils {

    // 默认匹配 ${...} 格式占位符的正则表达式
    private static final Pattern DEFAULT_REGEX = Pattern.compile("\\$\\{[^}]+\\}");


    private PptxClearUtils() {
    }

    /**
     * 一键清理入口（使用默认的 ${...} 正则表达式）
     *
     * @param ppt XMLSlideShow 对象
     */
    public static void process(XMLSlideShow ppt) {
        process(ppt, DEFAULT_REGEX);
    }

    /**
     * 一键清理入口（支持自定义占位符正则表达式，如 {{...}} ）
     *
     * @param ppt   XMLSlideShow 对象
     * @param regex 自定义占位符的正则表达式
     */
    public static void process(XMLSlideShow ppt, Pattern regex) {
        if (ppt == null) {
            return;
        }

        for (XSLFSlide slide : ppt.getSlides()) {
            // 暂存本页中需要被彻底删除的空白文本框，避免遍历时直接删除触发并发修改异常
            List<XSLFTextShape> toRemoveShapes = new ArrayList<>();

            // 递归检查并清理文本
            inspectAndCleanContainer(slide, regex, toRemoveShapes);

            // 统一从幻灯片中移除这些无用的空白文本框
            for (XSLFTextShape shape : toRemoveShapes) {
                removeShapeFromContainer(slide, shape);
            }
        }
        LOGGER.info("占位符与空白文本框清理完成");
    }

    /**
     * 递归检查容器（支持 Slide 顶层和 GroupShape 内部）
     */
    private static void inspectAndCleanContainer(XSLFShapeContainer container, Pattern regex, List<XSLFTextShape> toRemoveShapes) {
        for (XSLFShape shape : container.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                XSLFTextShape textShape = (XSLFTextShape) shape;
                String fullText = textShape.getText();

                // 1. 如果文本框本来就是空的，或者全是空白字符，直接标记准备删除
                if (fullText == null || fullText.trim().isEmpty()) {
                    toRemoveShapes.add(textShape);
                    continue;
                }

                // 2. 使用正则擦除残留的占位符
                cleanResidualPlaceholders(textShape, regex);

                // 3. 擦除完后再次检查，如果文本框变空了，也标记准备删除
                if (textShape.getText() == null || textShape.getText().trim().isEmpty()) {
                    toRemoveShapes.add(textShape);
                }

            } else if (shape instanceof XSLFGroupShape) {
                // 如果是组合形状，递归向下清理
                inspectAndCleanContainer((XSLFGroupShape) shape, regex, toRemoveShapes);
            }
        }
    }

    /**
     * 精准擦除段落（Paragraph）和文本块（Run）中残留的占位符
     */
    private static void cleanResidualPlaceholders(XSLFTextShape shape, Pattern regex) {
        for (XSLFTextParagraph p : shape.getTextParagraphs()) {
            List<XSLFTextRun> runs = p.getTextRuns();
            if (runs.isEmpty()) {
                continue;
            }
            // 获取当前段落的完整文本
            StringBuilder sb = new StringBuilder();
            for (XSLFTextRun r : runs) {
                sb.append(r.getRawText());
            }
            String content = sb.toString();
            // 正则匹配检查
            Matcher matcher = regex.matcher(content);
            if (matcher.find()) {
                // 将所有匹配到的占位符全部替换为 ""
                String result = matcher.replaceAll("");
                // 写回第一个 Run，并清理掉该段落后续的多余 Runs 保证格式不乱
                runs.get(0).setText(result);
                for (int i = runs.size() - 1; i > 0; i--) {
                    p.removeTextRun(runs.get(i));
                }
            }
        }
    }

    /**
     * 辅助方法：安全地从顶层或组合形状内移除指定的 Shape
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
}