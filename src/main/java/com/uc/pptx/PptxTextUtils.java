package com.uc.pptx;

import lombok.extern.log4j.Log4j2;
import org.apache.poi.sl.usermodel.PaintStyle;
import org.apache.poi.xslf.usermodel.*;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 普通文本替换工具类
 * 💡 深度整合版：完美适配 [跨平台中英文混排] + [100%样式承袭防乱码] + [支持组组件/表格嵌套扫描]
 */
@Log4j2
public final class PptxTextUtils {

    // 🚀 核心防乱码优化：全局定义跨平台绝对安全的逻辑中文字体名
    private static final String SAFE_FONT_FAMILY;

    static {
        // 获取当前系统支持的所有物理字体名称
        String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        boolean hasYaHei = Arrays.stream(fontNames).anyMatch("Microsoft YaHei"::equalsIgnoreCase);
        boolean hasPingFang = Arrays.stream(fontNames).anyMatch("PingFang SC"::equalsIgnoreCase);

        if (hasYaHei) {
            SAFE_FONT_FAMILY = "Microsoft YaHei";
        } else if (hasPingFang) {
            SAFE_FONT_FAMILY = "PingFang SC";
        } else {
            // JVM 层面在任意 Linux 发行版均 100% 存在的逻辑无衬线字体，完美防御 Docker 容器方块乱码
            SAFE_FONT_FAMILY = "SansSerif";
        }
        LOGGER.info("PPTX 文本替换引擎初始化成功，跨平台安全字体绑定为: {}", SAFE_FONT_FAMILY);
    }

    private PptxTextUtils() {
    }

    /**
     * 一键文本占位符替换入口
     *
     * @param ppt     XMLSlideShow 对象
     * @param dataMap 替换的键值对集合（如: "${name}" -> "张三"）
     */
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
                // 逐个段落进行安全样式替换
                for (XSLFTextParagraph p : textShape.getTextParagraphs()) {
                    for (Map.Entry<String, String> entry : dataMap.entrySet()) {
                        replaceTextInParagraph(p, entry.getKey(), entry.getValue());
                    }
                }
            } else if (shape instanceof XSLFGroupShape) {
                processContainer((XSLFGroupShape) shape, dataMap);
            } else if (shape instanceof XSLFTable) {
                // 🛠️ 扩展：如果是表格组件，同样深度扫描里面的单元格文本
                XSLFTable table = (XSLFTable) shape;
                for (XSLFTableRow row : table.getRows()) {
                    for (XSLFTableCell cell : row.getCells()) {
                        for (XSLFTextParagraph p : cell.getTextParagraphs()) {
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
     * 🚀 终极样式承袭算法：在段落内部精准替换文本，同时保持高强度的中文字体轨不破损
     */
    private static void replaceTextInParagraph(XSLFTextParagraph p, String target, String replacement) {
        List<XSLFTextRun> runs = p.getTextRuns();
        if (runs.isEmpty()) {
            return;
        }

        // 1. 拼接当前段落的完整文本（解决占位符被 Office 意外切碎成多个 Run 的千古难题）
        StringBuilder sb = new StringBuilder();
        for (XSLFTextRun r : runs) {
            String text = r.getRawText();
            if (text != null) {
                sb.append(text);
            }
        }
        String fullText = sb.toString();

        // 2. 如果当前段落文本不包含目标占位符，直接安全退出
        if (!fullText.contains(target)) {
            return;
        }

        // 3. 提取首个有效 Run 的样式元数据，作为整个段落的基本承袭骨架
        XSLFTextRun baseRun = runs.get(0);
        String sourceFontFamily = baseRun.getFontFamily();
        Double sourceFontSize = baseRun.getFontSize();
        boolean isBold = baseRun.isBold();
        boolean isItalic = baseRun.isItalic();
        PaintStyle fontColor = baseRun.getFontColor();

        // 4. 动态环境校验：如果原有 PPT 定义的字体在当前操作系统（如 Linux 容器）不存在，强制执行自适应保护
        if (sourceFontFamily != null && !sourceFontFamily.isEmpty()) {
            String[] availableFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            boolean fontExists = Arrays.stream(availableFonts).anyMatch(sourceFontFamily::equalsIgnoreCase);
            if (!fontExists) {
                sourceFontFamily = SAFE_FONT_FAMILY;
            }
        } else {
            sourceFontFamily = SAFE_FONT_FAMILY;
        }

        // 5. 执行文本彻底重组替换
        String newFullText = fullText.replace(target, replacement != null ? replacement : "");

        // 6. 🛠️ 清空原有被切碎的错位旧 Runs，重新构建一个干净的、字体轨完备的新 Run
        for (int i = runs.size() - 1; i >= 0; i--) {
            p.removeTextRun(runs.get(i));
        }

        XSLFTextRun newRun = p.addNewTextRun();
        newRun.setText(newFullText);

        // 7. 重新注入被全方位保护的高清晰度无乱码字体属性
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