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
 * 纯 Java 原生实现桑基图生成工具（高解耦、自适应比例优化版）
 */
@Log4j2
public final class SankeyChartUtils {

    // 预置高雅现代莫兰迪色系调色板
    private static final Color[] PALETTE = {
            new Color(44, 122, 123), new Color(214, 120, 45),
            new Color(40, 150, 90), new Color(200, 60, 60),
            new Color(111, 76, 182), new Color(108, 117, 125),
            new Color(30, 100, 180), new Color(160, 120, 50)
    };

    private SankeyChartUtils() {
    }

    /**
     * 一键生成桑基图并导出为本地图片
     */
    public static void processSankey(String title, List<SankeyEntity> sankeyEntityList, String outputPath, int width, int height) {
        if (sankeyEntityList == null || sankeyEntityList.isEmpty()) {
            return;
        }

        // 1. 初始化高精度画布与抗锯齿
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        // 绘制标题
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Microsoft YaHei", Font.BOLD, 20));
        g2d.drawString(title, 40, 45);

        // =========================================================
        // 🚀 优化点 1：高性能动态拓扑分层（只计算一次，彻底移除后续重写死循环）
        // =========================================================
        Map<String, Integer> nodeLevels = new HashMap<>();
        for (SankeyEntity sankeyEntity : sankeyEntityList) {
            nodeLevels.put(sankeyEntity.getSource(), 0);
            nodeLevels.put(sankeyEntity.getTarget(), 0);
        }

        boolean changed = true;
        int maxPossibleIters = nodeLevels.size();
        int iterCount = 0;

        while (changed && iterCount < maxPossibleIters) {
            changed = false;
            iterCount++;
            for (SankeyEntity sankeyEntity : sankeyEntityList) {
                int srcLevel = nodeLevels.get(sankeyEntity.getSource());
                int tgtLevel = nodeLevels.get(sankeyEntity.getTarget());
                if (tgtLevel <= srcLevel) {
                    nodeLevels.put(sankeyEntity.getTarget(), srcLevel + 1);
                    changed = true;
                }
            }
        }

        int maxLevel = nodeLevels.isEmpty() ? 0 : Collections.max(nodeLevels.values());
        int totalLevels = maxLevel + 1;

        // 建立层级到节点的映射
        Map<Integer, List<String>> levelNodesMap = new HashMap<>();
        for (Map.Entry<String, Integer> entry : nodeLevels.entrySet()) {
            levelNodesMap.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        // =========================================================
        // 🚀 优化点 2：单次循环聚合流量权重，避免在渲染时多重嵌套循环
        // =========================================================
        Map<String, Double> nodeVolumeMap = new HashMap<>();
        double totalValue = 0;
        for (SankeyEntity sankeyEntity : sankeyEntityList) {
            String src = sankeyEntity.getSource();
            String tgt = sankeyEntity.getTarget();
            double val = sankeyEntity.getValue();

            nodeVolumeMap.put(src, nodeVolumeMap.getOrDefault(src, 0.0) + val);
            nodeVolumeMap.put(tgt, nodeVolumeMap.getOrDefault(tgt, 0.0) + val);

            // 以根节点总流出量作为全局缩放基准
            if (nodeLevels.get(src) == 0) {
                totalValue += val;
            }
        }
        if (totalValue == 0) totalValue = 1.0; // 严防除零异常

        // 2. 绘图几何布局配置
        int paddingLeft = 160;
        int paddingRight = 180;
        int startY = 110;
        int marginBottom = 90;
        int availableWidth = width - paddingLeft - paddingRight;
        int availableHeight = height - startY - marginBottom;
        int nodeWidth = 24;
        int gap = 14; // 节点垂直视觉间距

        // 3. 计算所有节点在画布上的精确 Rectangle 坐标
        Map<String, Rectangle> nodeBounds = new HashMap<>();
        int levelSpacing = totalLevels > 1 ? availableWidth / (totalLevels - 1) : availableWidth;

        for (int lvl = 0; lvl < totalLevels; lvl++) {
            List<String> nodesInLevel = levelNodesMap.get(lvl);
            if (nodesInLevel == null) continue;

            int x = paddingLeft + lvl * levelSpacing;
            int currentY = startY;

            // 动态扣除当前层所有 gap 占用的空间，剩余的才是节点真正可分配的高度
            int usableHeight = availableHeight - ((nodesInLevel.size() - 1) * gap);

            for (String node : nodesInLevel) {
                // 优化：不再使用复杂的 getFinalVal，直接取聚合后的最大单向吞吐量
                double nodeVal = nodeVolumeMap.getOrDefault(node, 0.0);
                // 桑基图节点高度公式
                int nodeHeight = (int) ((nodeVal / 2.0 / totalValue) * usableHeight);
                if (lvl == 0 || lvl == maxLevel) {
                    nodeHeight = (int) ((nodeVal / totalValue) * usableHeight); // 边界层无双向叠加，直接计算
                }
                if (nodeHeight < 12) nodeHeight = 12; // 优雅的硬托底，防止小流量节点蒸发

                nodeBounds.put(node, new Rectangle(x, currentY, nodeWidth, nodeHeight));
                currentY += nodeHeight + gap;
            }
        }

        // =========================================================
        // 🚀 优化点 3：完美的“自适应局部比例尺”管道算法（解决管道过粗溢出）
        // =========================================================
        Map<String, Integer> sourceOffsets = new HashMap<>();
        Map<String, Integer> targetOffsets = new HashMap<>();

        for (SankeyEntity sankeyEntity : sankeyEntityList) {
            String src = sankeyEntity.getSource();
            String tgt = sankeyEntity.getTarget();
            double val = sankeyEntity.getValue();

            Rectangle srcRect = nodeBounds.get(src);
            Rectangle tgtRect = nodeBounds.get(tgt);
            if (srcRect == null || tgtRect == null) continue;

            int sOff = sourceOffsets.getOrDefault(src, 0);
            int tOff = targetOffsets.getOrDefault(tgt, 0);

            // 📐 局部精细比例尺：单条边粗细应该严格参照它在源节点高度中所占的比例
            double srcTotal = sankeyEntityList.stream().filter(e -> e.getSource().equals(src)).mapToDouble(SankeyEntity::getValue).sum();
            int pipeHeight = (int) ((val / (srcTotal > 0 ? srcTotal : 1)) * srcRect.height);
            if (pipeHeight < 2) pipeHeight = 2;

            // 如果计算误差导致管道超出矩形，进行安全截断
            if (sOff + pipeHeight > srcRect.height) pipeHeight = srcRect.height - sOff;

            int x1 = srcRect.x + srcRect.width;
            int y1 = srcRect.y + sOff + (pipeHeight / 2);
            int x2 = tgtRect.x;
            int y2 = tgtRect.y + tOff + (pipeHeight / 2);

            CubicCurve2D curve = new CubicCurve2D.Float(
                    x1, y1,
                    x1 + (x2 - x1) / 2f, y1,
                    x1 + (x2 - x1) / 2f, y2,
                    x2, y2
            );

            // 4. 渲染半透明优雅流向
            Color srcColor = PALETTE[Math.abs(src.hashCode()) % PALETTE.length];
            g2d.setColor(new Color(srcColor.getRed(), srcColor.getGreen(), srcColor.getBlue(), 70)); // 70 的透明度叠加层级感最好
            g2d.setStroke(new BasicStroke(pipeHeight, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g2d.draw(curve);

            sourceOffsets.put(src, sOff + pipeHeight);
            targetOffsets.put(tgt, tOff + pipeHeight);
        }

        // 5. 统一覆盖绘制节点遮罩与抗截断文本
        g2d.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        for (Map.Entry<String, Rectangle> entry : nodeBounds.entrySet()) {
            String nodeName = entry.getKey();
            Rectangle rect = entry.getValue();

            Color nodeColor = PALETTE[Math.abs(nodeName.hashCode()) % PALETTE.length];
            g2d.setColor(nodeColor);
            g2d.fill(rect);

            g2d.setColor(Color.BLACK);
            int textY = rect.y + (rect.height / 2) + 5;

            // 最后一层文字靠右书写，其余层一律靠左，规避长文本跨层重叠缺陷
            if (nodeLevels.get(nodeName) == maxLevel) {
                g2d.drawString(nodeName, rect.x + rect.width + 8, textY);
            } else {
                g2d.drawString(nodeName, rect.x - g2d.getFontMetrics().stringWidth(nodeName) - 8, textY);
            }
        }

        // 6. 释放资源与安全落盘
        g2d.dispose();
        File file = new File(outputPath);
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        try {
            ImageIO.write(image, "png", file);
        } catch (IOException e) {
            LOGGER.error("桑基图导出图片 IO 失败, 路径: {}", file.getPath(), e);
            throw new RuntimeException(e);
        }
    }
}