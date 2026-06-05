package com.uc.pptx.processor;

import cn.hutool.core.util.StrUtil;
import org.apache.poi.xslf.usermodel.*;

import java.util.List;
import java.util.Map;

/**
 * 专门负责 PPT 普通文本替换
 */
public class PptxTextProcessor {

    public static void process(XMLSlideShow ppt, Map<String, String> dataMap) {
        if (ppt == null || dataMap == null || dataMap.isEmpty()) return;

        for (XSLFSlide slide : ppt.getSlides()) {
            processContainer(slide, dataMap);
        }
    }

    private static void processContainer(XSLFShapeContainer container, Map<String, String> dataMap) {
        for (XSLFShape shape : container.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                XSLFTextShape textShape = (XSLFTextShape) shape;
                String fullText = textShape.getText();
                if (fullText == null || fullText.isEmpty()) continue;

                for (Map.Entry<String, String> entry : dataMap.entrySet()) {
                    String key = entry.getKey();
                    if (StrUtil.containsAnyIgnoreCase(fullText, key)) {
                        replaceText(textShape, key, entry.getValue());
                        fullText = textShape.getText(); // 刷新文本供下一个Key匹配
                    }
                }
            } else if (shape instanceof XSLFGroupShape) {
                processContainer((XSLFGroupShape) shape, dataMap);
            }
        }
    }

    private static void replaceText(XSLFTextShape shape, String target, String replacement) {
        for (XSLFTextParagraph p : shape.getTextParagraphs()) {
            List<XSLFTextRun> runs = p.getTextRuns();
            if (runs.isEmpty()) continue;

            StringBuilder sb = new StringBuilder();
            for (XSLFTextRun r : runs) sb.append(r.getRawText());
            String content = sb.toString();

            if (content.contains(target)) {
                String result = content.replace(target, replacement);
                runs.get(0).setText(result);
                for (int i = runs.size() - 1; i > 0; i--) {
                    p.removeTextRun(runs.get(i));
                }
            }
        }
    }
}