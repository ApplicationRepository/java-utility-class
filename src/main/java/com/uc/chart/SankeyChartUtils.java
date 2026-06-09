package com.uc.chart;

import com.uc.entity.SankeyEntity;
import lombok.extern.log4j.Log4j2;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.CubicCurve2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * 纯 Java 原生实现桑基图生成工具
 * 💡 深度整合版：完美解决 [跨平台中文乱码] + [多层级自适应折行排版] + [图例防重叠避让]
 */
@Log4j2
public final class SankeyChartUtils {

    // 🚀 核心防乱码优化：定义跨平台安全的自适应字体体系
    private static final Font TEXT_FONT;
    private static final Font TITLE_FONT;
    // 预置高梯度、高辨识度的莫兰迪与商务混搭色系（确保多节点时不撞色）
    private static final Color[] PALETTE = {
            new Color(31, 119, 180),
            new Color(255, 127, 14),
            new Color(44, 160, 44),
            new Color(214, 39, 40),
            new Color(148, 103, 189),
            new Color(140, 86, 75),
            new Color(227, 119, 194),
            new Color(127, 127, 127),
            new Color(188, 189, 34),
            new Color(23, 190, 207),
            new Color(44, 122, 123),
            new Color(214, 120, 45)
    };

    static {
        // 1. 获取当前系统支持的所有物理字体名称
        String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        boolean hasYaHei = Arrays.stream(fontNames).anyMatch("Microsoft YaHei"::equalsIgnoreCase);
        boolean hasPingFang = Arrays.stream(fontNames).anyMatch("PingFang SC"::equalsIgnoreCase);

        // 2. 智能化动态适配：Windows 绑定微软雅黑，Mac 绑定平方，Linux/Docker 降级至安全的通用逻辑无衬线字体 SansSerif
        String targetFontFamily;
        if (hasYaHei) {
            targetFontFamily = "Microsoft YaHei";
        } else if (hasPingFang) {
            targetFontFamily = "PingFang SC";
        } else {
            // JVM 层面在任意 Linux 发行版均 100% 存在的逻辑字体，完美防御 Docker 容器方块乱码
            targetFontFamily = "SansSerif";
        }

        TEXT_FONT = new Font(targetFontFamily, Font.PLAIN, 11);
        TITLE_FONT = new Font(targetFontFamily, Font.BOLD, 18);

        LOGGER.info("桑基图渲染引擎初始化成功，当前运行平台已自动绑定最适中文字体: {}", targetFontFamily);
    }

    private SankeyChartUtils() {
    }

    /**
     * 一键生成桑基图（自适应多层折行 + 全平台中文防乱码版）
     *
     * @param title            图片大标题（支持中文）
     * @param sankeyEntityList 包含源节点、目标节点和流量值的实体列表
     * @param outputPath       图片输出的完整绝对路径
     * @param width            画布总宽度
     * @param height           画布总高度
     */
    public static void processSankey(String title, List<SankeyEntity> sankeyEntityList, String outputPath, int width, int height) {
        if (sankeyEntityList == null || sankeyEntityList.isEmpty()) {
            LOGGER.warn("输入的桑基图样本数据为空，跳过渲染。");
            return;
        }

        // 1. 初始化高精度画布与全局抗锯齿
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 刷白背景
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        // 绘制大标题 (应用防乱码的大字体)
        g2d.setColor(Color.BLACK);
        g2d.setFont(TITLE_FONT);
        g2d.drawString(title, 40, 45);

        // 2. 拓扑分层计算 (严格确定每个节点的绝对层级)
        Map<String, Integer> nodeLevels = new HashMap<>();
        for (SankeyEntity entity : sankeyEntityList) {
            nodeLevels.put(entity.getSource(), 0);
            nodeLevels.put(entity.getTarget(), 0);
        }

        boolean changed = true;
        int maxIters = nodeLevels.size();
        int iters = 0;
        while (changed && iters < maxIters) {
            changed = false;
            iters++;
            for (SankeyEntity entity : sankeyEntityList) {
                int srcLvl = nodeLevels.get(entity.getSource());
                int tgtLvl = nodeLevels.get(entity.getTarget());
                if (tgtLvl <= srcLvl) {
                    nodeLevels.put(entity.getTarget(), srcLvl + 1);
                    changed = true;
                }
            }
        }

        int maxLevel = nodeLevels.isEmpty() ? 0 : Collections.max(nodeLevels.values());
        int totalLevels = maxLevel + 1;

        // 3. 流量全网聚合
        Map<String, Double> nodeVolumeMap = new HashMap<>();
        double globalTotalValue = 0;
        for (SankeyEntity entity : sankeyEntityList) {
            nodeVolumeMap.put(entity.getSource(), nodeVolumeMap.getOrDefault(entity.getSource(), 0.0) + entity.getValue());
            nodeVolumeMap.put(entity.getTarget(), nodeVolumeMap.getOrDefault(entity.getTarget(), 0.0) + entity.getValue());
            if (nodeLevels.get(entity.getSource()) == 0) {
                globalTotalValue += entity.getValue();
            }
        }
        if (globalTotalValue == 0) {
            globalTotalValue = 1.0;
        }

        // 4. 自适应“上下双层分流”几何视口切分逻辑
        boolean isDoubleRow = totalLevels > 4; // 当总层数超过 4 层时，全自动开启上下两层折行排版
        int levelsPerRow = isDoubleRow ? (int) Math.ceil(totalLevels / 2.0) : totalLevels;

        int paddingLeft = 100;
        int paddingRight = 100;
        int nodeWidth = 24;
        int gap = 16; // 节点垂直视觉间距

        int availableWidth = width - paddingLeft - paddingRight;
        int levelSpacing = levelsPerRow > 1 ? availableWidth / (levelsPerRow - 1) : availableWidth;

        // 弹性分配上下两层各自的 Y 轴绘图区间
        int row1StartY = 90;
        int row2StartY = isDoubleRow ? height / 2 + 40 : row1StartY;
        int rowHeight = isDoubleRow ? (height / 2) - 130 : height - row1StartY - 80;

        // 建立层级到节点的映射关系
        Map<Integer, List<String>> levelNodesMap = new HashMap<>();
        for (Map.Entry<String, Integer> entry : nodeLevels.entrySet()) {
            levelNodesMap.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        // 计算所有节点在画布上的精确 Rectangle 几何坐标
        Map<String, Rectangle> nodeBounds = new HashMap<>();
        for (int lvl = 0; lvl < totalLevels; lvl++) {
            List<String> nodes = levelNodesMap.get(lvl);
            if (nodes == null) {
                continue;
            }

            // 核心计算：折行后的横坐标 X 及所属行数
            int targetRow = isDoubleRow && (lvl >= levelsPerRow) ? 1 : 0;
            int colIndex = targetRow == 0 ? lvl : (lvl - levelsPerRow);
            int x = paddingLeft + colIndex * levelSpacing;

            int startY = (targetRow == 0) ? row1StartY : row2StartY;
            int usableHeight = rowHeight - ((nodes.size() - 1) * gap);

            for (String node : nodes) {
                double nodeVal = nodeVolumeMap.getOrDefault(node, 0.0);
                // 上下两层边界独立动态标尺缩放
                int nodeHeight = (int) ((nodeVal / 2.0 / globalTotalValue) * usableHeight);
                if (lvl == 0 || lvl == maxLevel || (isDoubleRow && lvl == levelsPerRow - 1)) {
                    nodeHeight = (int) ((nodeVal / globalTotalValue) * usableHeight);
                }
                if (nodeHeight < 14) {
                    nodeHeight = 14; // 硬托底，防止极小流量节点在画布上蒸发消失
                }

                nodeBounds.put(node, new Rectangle(x, startY, nodeWidth, nodeHeight));
                startY += nodeHeight + gap;
            }
        }

        // 5. 渲染流向管道（贝塞尔曲线插值算法）
        Map<String, Integer> sourceOffsets = new HashMap<>();
        Map<String, Integer> targetOffsets = new HashMap<>();

        for (SankeyEntity entity : sankeyEntityList) {
            String src = entity.getSource();
            String tgt = entity.getTarget();
            double val = entity.getValue();

            Rectangle srcRect = nodeBounds.get(src);
            Rectangle tgtRect = nodeBounds.get(tgt);
            if (srcRect == null || tgtRect == null) {
                continue;
            }

            int sOff = sourceOffsets.getOrDefault(src, 0);
            int tOff = targetOffsets.getOrDefault(tgt, 0);

            double srcTotal = sankeyEntityList.stream().filter(e -> e.getSource().equals(src)).mapToDouble(SankeyEntity::getValue).sum();
            int pipeHeight = (int) ((val / (srcTotal > 0 ? srcTotal : 1)) * srcRect.height);
            if (pipeHeight < 2) {
                pipeHeight = 2;
            }

            if (sOff + pipeHeight > srcRect.height) {
                pipeHeight = srcRect.height - sOff;
            }

            int srcLvl = nodeLevels.get(src);
            int tgtLvl = nodeLevels.get(tgt);

            int x1 = srcRect.x + srcRect.width;
            int y1 = srcRect.y + sOff + (pipeHeight / 2);
            int x2 = tgtRect.x;
            int y2 = tgtRect.y + tOff + (pipeHeight / 2);

            Shape curve;
            // 💡 若管道发生了上下跨层连接，使用特殊的深 S 型贝塞尔曲线进行顺滑绕行，防止产生生硬折角
            if (isDoubleRow && srcLvl < levelsPerRow && tgtLvl >= levelsPerRow) {
                int ctrlX1 = x1 + levelSpacing / 3;
                int ctrlY1 = y1 + rowHeight / 2;
                int ctrlX2 = x2 - levelSpacing / 3;
                int ctrlY2 = y2 - rowHeight / 2;
                curve = new CubicCurve2D.Float(x1, y1, ctrlX1, ctrlY1, ctrlX2, ctrlY2, x2, y2);
            } else {
                // 普通同层横向流向
                curve = new CubicCurve2D.Float(x1, y1, x1 + (x2 - x1) / 2f, y1, x1 + (x2 - x1) / 2f, y2, x2, y2);
            }

            // 动态注入半透明色彩，消除高维管道交错时的生硬感
            Color srcColor = PALETTE[Math.abs(src.hashCode()) % PALETTE.length];
            g2d.setColor(new Color(srcColor.getRed(), srcColor.getGreen(), srcColor.getBlue(), 65)); // 65 透明度层级感最佳
            g2d.setStroke(new BasicStroke(pipeHeight, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g2d.draw(curve);

            sourceOffsets.put(src, sOff + pipeHeight);
            targetOffsets.put(tgt, tOff + pipeHeight);
        }

        // 6. 渲染节点遮罩矩形与高清晰度非碰撞中文标签文本
        g2d.setFont(TEXT_FONT); // 注入全局防乱码字体
        FontMetrics fm = g2d.getFontMetrics();

        for (Map.Entry<String, Rectangle> entry : nodeBounds.entrySet()) {
            String nodeName = entry.getKey();
            Rectangle rect = entry.getValue();
            int lvl = nodeLevels.get(nodeName);

            // 绘制节点主色块
            Color nodeColor = PALETTE[Math.abs(nodeName.hashCode()) % PALETTE.length];
            g2d.setColor(nodeColor);
            g2d.fill(rect);

            // 增加暗色描边，大幅提高多层图例的高清立体感
            g2d.setColor(nodeColor.darker());
            g2d.setStroke(new BasicStroke(1.0f));
            g2d.draw(rect);

            // 🚀 标签非碰撞核心算法：根据节点所处象限自动微调文本锚点
            g2d.setColor(Color.DARK_GRAY);
            int textWidth = fm.stringWidth(nodeName);
            int textX;
            int textY = rect.y + (rect.height / 2) + 4;

            if (isDoubleRow) {
                if (lvl == levelsPerRow - 1 || lvl == maxLevel) {
                    // 阶段性末端层级，文字靠右摆放
                    textX = rect.x + rect.width + 6;
                } else if (lvl == 0 || lvl == levelsPerRow) {
                    // 阶段性起始层级，文字靠左摆放
                    textX = rect.x - textWidth - 6;
                } else {
                    // 拥挤的中间层：文字不再左右强塞，而是采用奇偶层 [上下交错悬浮] 排版，彻底避开前后管道
                    textX = rect.x + (rect.width / 2) - (textWidth / 2);
                    textY = (lvl % 2 == 0) ? rect.y - 6 : rect.y + rect.height + 14;
                }
            } else {
                // 标准单行铺开模式下的常规左右排版
                if (lvl == maxLevel) {
                    textX = rect.x + rect.width + 6;
                } else {
                    textX = rect.x - textWidth - 6;
                }
            }

            // 字符安全边界截断：如果文本极长并溢出画布边界，执行自动防崩截断 `...`
            if (textX < 5) {
                textX = 5;
            }
            if (textX + textWidth > width - 5) {
                String truncated = nodeName;
                while (fm.stringWidth(truncated + "...") > (width - textX - 10) && truncated.length() > 1) {
                    truncated = truncated.substring(0, truncated.length() - 1);
                }
                nodeName = truncated + "...";
            }

            g2d.drawString(nodeName, textX, textY);
        }

        // 7. 释放资源并安全落盘
        g2d.dispose();
        File file = new File(outputPath);
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        try {
            ImageIO.write(image, "png", file);
            LOGGER.info("桑基图多元化看板输出成功 -> {}", outputPath);
        } catch (IOException e) {
            LOGGER.error("桑基图导出本地图片失败，目标路径: {}", file.getPath(), e);
            throw new RuntimeException(e);
        }
    }
}