package com.uc.pptx;


import lombok.extern.log4j.Log4j2;
import org.apache.poi.xslf.usermodel.*;

import java.util.List;
import java.util.Map;

/**
 * 普通文本替换
 */
@Log4j2
public final class PptxTextUtils {

    private PptxTextUtils() {
    }

    public static void process(XMLSlideShow ppt, Map<String, String> dataMap) {
        if (ppt == null || dataMap == null || dataMap.isEmpty()) {
            return;
        }
        for (XSLFSlide slide : ppt.getSlides()) {
            processContainer(slide, dataMap);
        }
        LOGGER.info("文本生成完毕");
    }

    private static void processContainer(XSLFShapeContainer container, Map<String, String> dataMap) {
        for (XSLFShape shape : container.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                XSLFTextShape textShape = (XSLFTextShape) shape;
                // 核心：逐个段落进行安全样式替换
                for (XSLFTextParagraph p : textShape.getTextParagraphs()) {
                    for (Map.Entry<String, String> entry : dataMap.entrySet()) {
                        replaceTextInParagraph(p, entry.getKey(), entry.getValue());
                    }
                }
            } else if (shape instanceof XSLFGroupShape) {
                processContainer((XSLFGroupShape) shape, dataMap);
            }
        }
    }

    /**
     * 核心算法：在段落内部替换文本，同时保留每个 Run 的独立样式
     */
    private static void replaceTextInParagraph(XSLFTextParagraph p, String target, String replacement) {
        List<XSLFTextRun> runs = p.getTextRuns();
        if (runs.isEmpty()) {
            return;
        }
        // 1. 拼接当前段落的完整文本
        StringBuilder sb = new StringBuilder();
        for (XSLFTextRun r : runs) {
            sb.append(r.getRawText());
        }
        String fullText = sb.toString();

        // 2. 如果不包含占位符，直接退出
        if (!fullText.contains(target)) {
            return;
        }

        // 3. 计算替换后的最终新文本
        String newFullText = fullText.replace(target, replacement);

        // 4. 【高能保留样式算法】将新文本按原有的 Run 长度和样式重新分配回去
        int currentPos = 0;
        for (int i = 0; i < runs.size(); i++) {
            XSLFTextRun run = runs.get(i);
            int origRunLen = run.getRawText().length();

            if (currentPos >= newFullText.length()) {
                // 如果新文本已经分配完了，剩下的 Run 置为空白（但保留其样式壳子，防止报错）
                run.setText("");
                continue;
            }
            if (i == runs.size() - 1) {
                // 如果是最后一个 Run，把剩下所有的新文本全吞掉
                run.setText(newFullText.substring(currentPos));
            } else {
                // 否则，按照原来这个位置的 Run 的大致长度进行截取分配
                // 这样能保证在这个 Run 范围内的文字，100% 继承它原本的字体、颜色、加粗
                int nextPos = Math.min(currentPos + origRunLen, newFullText.length());
                run.setText(newFullText.substring(currentPos, nextPos));
                currentPos = nextPos;
            }
        }
    }
}