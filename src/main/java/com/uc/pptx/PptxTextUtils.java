package com.uc.pptx;

import lombok.extern.log4j.Log4j2;
import org.apache.poi.sl.usermodel.PaintStyle;
import org.apache.poi.xslf.usermodel.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 普通文本替换工具类
 * 💡 深度整合版：彻底解决 [ConcurrentModificationException] 与 [XmlValueDisconnectedException]
 */
@Log4j2
public final class PptxTextUtils {

    private static final String SAFE_FONT_FAMILY;

    static {
        String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        boolean hasYaHei = Arrays.stream(fontNames).anyMatch("Microsoft YaHei"::equalsIgnoreCase);
        boolean hasPingFang = Arrays.stream(fontNames).anyMatch("PingFang SC"::equalsIgnoreCase);

        if (hasYaHei) {
            SAFE_FONT_FAMILY = "Microsoft YaHei";
        } else if (hasPingFang) {
            SAFE_FONT_FAMILY = "PingFang SC";
        } else {
            SAFE_FONT_FAMILY = "SansSerif";
        }
        LOGGER.info("PPTX 文本替换引擎初始化成功，跨平台安全字体绑定为: {}", SAFE_FONT_FAMILY);
    }

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

                // 🚀 优化 1：通过创建 ArrayList 镜像，彻底阻断 ConcurrentModificationException
                List<XSLFTextParagraph> paragraphs = new ArrayList<>(textShape.getTextParagraphs());
                for (XSLFTextParagraph p : paragraphs) {
                    for (Map.Entry<String, String> entry : dataMap.entrySet()) {
                        replaceTextInParagraph(p, entry.getKey(), entry.getValue());
                    }
                }
            } else if (shape instanceof XSLFGroupShape) {
                processContainer((XSLFGroupShape) shape, dataMap);
            } else if (shape instanceof XSLFTable) {
                XSLFTable table = (XSLFTable) shape;
                for (XSLFTableRow row : table.getRows()) {
                    for (XSLFTableCell cell : row.getCells()) {
                        // 🚀 同样对表格单元格内的段落进行 ArrayList 快照镜像化保护
                        List<XSLFTextParagraph> cellParas = new ArrayList<>(cell.getTextParagraphs());
                        for (XSLFTextParagraph p : cellParas) {
                            for (Map.Entry<String, String> entry : dataMap.entrySet()) {
                                replaceTextInParagraph(p, entry.getKey(), entry.getValue());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 🚀 终极样式承袭算法：采用深度离线拷贝，彻底根绝 XmlValueDisconnectedException
     */
    private static void replaceTextInParagraph(XSLFTextParagraph p, String target, String replacement) {
        List<XSLFTextRun> runs = p.getTextRuns();
        if (runs.isEmpty()) {
            return;
        }

        // 1. 拼接当前段落的完整文本
        StringBuilder sb = new StringBuilder();
        for (XSLFTextRun r : runs) {
            String text = r.getRawText();
            if (text != null) {
                sb.append(text);
            }
        }
        String fullText = sb.toString();

        if (!fullText.contains(target)) {
            return;
        }

        // 2. 🚀 优化 2：采用值复制进行“深拷贝”存储，绝不持有可能被销毁的 baseRun 节点的原始引用
        XSLFTextRun baseRun = runs.get(0);
        String sourceFontFamily = baseRun.getFontFamily();
        Double sourceFontSize = baseRun.getFontSize();
        boolean isBold = baseRun.isBold();
        boolean isItalic = baseRun.isItalic();
        PaintStyle fontColor = baseRun.getFontColor(); // 颜色基础值保留

        // 3. 动态环境校验与降级保护
        if (sourceFontFamily != null && !sourceFontFamily.isEmpty()) {
            String[] availableFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            boolean fontExists = Arrays.stream(availableFonts).anyMatch(sourceFontFamily::equalsIgnoreCase);
            if (!fontExists) {
                sourceFontFamily = SAFE_FONT_FAMILY;
            }
        } else {
            sourceFontFamily = SAFE_FONT_FAMILY;
        }

        // 4. 执行替换文本重组
        String newFullText = fullText.replace(target, replacement != null ? replacement : "");

        // 5. 安全清空陈旧零碎的旧 Runs
        // 💡 额外注意：清理时直接利用 p.removeTextRun(0)，比依赖 runs.get(i) 的外部索引更加对齐底层 XML 的变化
        int originalSize = runs.size();
        for (int i = 0; i < originalSize; i++) {
            if (!p.getTextRuns().isEmpty()) {
                p.removeTextRun(p.getTextRuns().get(0));
            }
        }

        // 6. 新建一个完备的、独立的全新 Run
        XSLFTextRun newRun = p.addNewTextRun();
        newRun.setText(newFullText);

        // 7. 🚀 安全注入深拷贝保留下来的样式属性（此时已脱离任何被破坏的 XML 节点的纠缠，绝不报错）
        newRun.setFontFamily(sourceFontFamily);
        if (sourceFontSize != null) {
            newRun.setFontSize(sourceFontSize);
        }
        newRun.setBold(isBold);
        newRun.setItalic(isItalic);
        if (fontColor != null) {
            newRun.setFontColor(fontColor);
        }
    }
}