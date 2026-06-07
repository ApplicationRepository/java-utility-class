package com.uc.pptx;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.*;

import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 专门负责将占位符文本框替换为【桑基图】
 */
@Log4j2
public final class PptxSankeyUtils {

    private PptxSankeyUtils() {
    }

    public static void process(XMLSlideShow ppt, Map<String, SankeyData> sankeyMap, String sankeyPlaceholderMark) {
        if (ppt == null || sankeyMap == null || sankeyMap.isEmpty()) {
            return;
        }

        for (XSLFSlide slide : ppt.getSlides()) {
            Map<XSLFTextShape, SankeyData> sankeyTasks = new HashMap<>();
            // 1. 扫描桑基图占位符
            scanSankeyPlaceholders(slide, sankeyMap, sankeyPlaceholderMark, sankeyTasks);
            // 2. 统一渲染替换
            sankeyTasks.forEach((shape, data) -> {
                try {
                    replaceShapeWithSankey(ppt, slide, shape, data);
                } catch (Exception e) {
                    LOGGER.error("桑基图替换失败", e);
                }
            });
        }
    }

    private static void scanSankeyPlaceholders(XSLFShapeContainer container, Map<String, SankeyData> sankeyMap, String mark, Map<XSLFTextShape, SankeyData> tasks) {
        for (XSLFShape shape : container.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                XSLFTextShape textShape = (XSLFTextShape) shape;
                String fullText = textShape.getText();
                if (fullText == null || fullText.isEmpty()) {
                    continue;
                }
                for (Map.Entry<String, SankeyData> entry : sankeyMap.entrySet()) {
                    if (StrUtil.containsAnyIgnoreCase(fullText, entry.getKey()) && entry.getKey().contains(mark)) {
                        tasks.put(textShape, entry.getValue());
                        break;
                    }
                }
            } else if (shape instanceof XSLFGroupShape) {
                scanSankeyPlaceholders((XSLFGroupShape) shape, sankeyMap, mark, tasks);
            }
        }
    }

    private static void replaceShapeWithSankey(XMLSlideShow ppt, XSLFSlide slide, XSLFTextShape textShape, SankeyData data) {
        Rectangle2D anchor = textShape.getAnchor();
        if (anchor == null) {
            return;
        }

        // 3. 调用生成渲染引擎，根据数据动态画出桑基图字节流
        // 传入宽和高（让生成的图片比例和 PPT 文本框完全一致，防止拉伸变形）
        byte[] sankeyImageBytes = generateSankeyImageBytes(data, (int) anchor.getWidth(), (int) anchor.getHeight());

        if (sankeyImageBytes == null || sankeyImageBytes.length == 0) {
            LOGGER.warn("桑基图生成字节为空");
            return;
        }

        // 4. 将生成的图表图片写入 PPT
        XSLFPictureData pd = ppt.addPicture(sankeyImageBytes, PictureData.PictureType.PNG);
        XSLFPictureShape pictureShape = slide.createPicture(pd);
        pictureShape.setAnchor(anchor);

        // 5. 移除原占位符
        removeShape(slide, textShape);
    }

    /**
     * 核心技术点：动态图表生成器
     * 实际落地时，可以通过调用后端 ECharts 节点的 HTTP 接口，
     * 或者使用 Java 的 Runtime 执行 PhantomJS/Node 脚本离线生成。
     */
    private static byte[] generateSankeyImageBytes(SankeyData data, int width, int height) {
        // 这里伪代码演示 ECharts Option 结构：
        // series: [{
        //     type: 'sankey',
        //     data: data.getNodes(),
        //     links: data.getLinks()
        // }]
        LOGGER.info("开始生成桑基图，画布大小: {}x{}, 节点数: {}", width, height, data.getNodes().size());

        try {
            // 【此处替换为你的 ECharts 离线导出工具类代码】
            // 示例：return EChartsExportUtil.export(sankeyOptionJson, width, height);

            return null;
        } catch (Exception e) {
            LOGGER.error("ECharts 离线渲染失败", e);
            return null;
        }
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

    /**
     * 桑基图数据包装内部类
     */
    @Data
    public static class SankeyData implements Serializable {
        private static final long serialVersionUID = 5000772254670024004L;
        private List<Map<String, String>> nodes; // [{name: 'A'}, {name: 'B'}]
        private List<Map<String, Object>> links; // [{source: 'A', target: 'B', value: 10}]

        public SankeyData(List<Map<String, String>> nodes, List<Map<String, Object>> links) {
            this.nodes = nodes;
            this.links = links;
        }
    }
}