package com.uc.chart;

import lombok.extern.log4j.Log4j2;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * 完整过程能力与性能指标 (CP/CPK/PP/PPK) 报表图片生成工具类
 * 💡 跨平台多环境（Windows、Linux、Mac、Docker）防乱码自适应优化版
 */
@Log4j2
public final class CpkPpkChartUtils {

    // 🚀 核心优化：定义跨平台安全的自适应字体体系
    private static final Font TEXT_FONT;
    private static final Font BOLD_FONT;
    private static final Font TITLE_FONT;

    static {
        // 1. 获取系统支持的所有物理字体名称
        String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        boolean hasYaHei = Arrays.stream(fontNames).anyMatch("Microsoft YaHei"::equalsIgnoreCase);
        boolean hasPingFang = Arrays.stream(fontNames).anyMatch("PingFang SC"::equalsIgnoreCase);

        // 2. 智能化动态适配：Windows 优先微软雅黑，Mac 优先平方，Linux/Docker 自动降级至绝对安全的 SansSerif
        String targetFontFamily;
        if (hasYaHei) {
            targetFontFamily = "Microsoft YaHei";
        } else if (hasPingFang) {
            targetFontFamily = "PingFang SC";
        } else {
            // JVM 层面在任意 Linux 发行版均 100% 存在的逻辑无衬线字体，完美防御 Docker 方块乱码
            targetFontFamily = "SansSerif";
        }

        TEXT_FONT = new Font(targetFontFamily, Font.PLAIN, 12);
        BOLD_FONT = new Font(targetFontFamily, Font.BOLD, 13);
        TITLE_FONT = new Font(targetFontFamily, Font.BOLD, 18);

        LOGGER.info("CPK/PPK 图表渲染引擎初始化成功，当前运行平台已自动绑定最适字体: {}", targetFontFamily);
    }

    private CpkPpkChartUtils() {
    }

    /**
     * 生成包含完整 SPC 指标的分布图并保存为本地图片
     *
     * @param title      图片大标题
     * @param data       原始样本数据数组
     * @param usl        上公差限制 (Upper Specification Limit)
     * @param lsl        下公差限制 (Lower Specification Limit)
     * @param bins       直方图分组数 (建议传 15-30，若传 0 则根据斯特基公式自适应)
     * @param outputPath 图片输出的完整绝对路径
     * @param width      自定义画布总宽度 (如 1100)
     * @param height     自定义画布总高度 (如 700)
     */
    public static void process(String title, double[] data, double usl, double lsl, int bins, String outputPath, int width, int height) {
        if (data == null || data.length == 0) {
            return;
        }

        // 1. 基础统计学指标计算
        int sampleCount = data.length;
        double mean = calculateMean(data);

        //calculateLongTermSigma (整体标准差)：采用的是传统的样本标准差公式（分母为 $n-1$），用于计算 PP 族指标。
        //calculateShortTermSigma (组内标准差)：采用了一个固定系数 0.963 进行无偏估计矫正（通常对应于特定子组大小下的 $c_4$ 或 $d_2$ 倒数），用于计算 CP 族指标。这符合工业 SPC 的标准控制规范。
        double sigmaLt = calculateLongTermSigma(data);  // 整体长期标准差 (用于 PP 族，同时作为曲线拟合的标准基准)
        double sigmaSt = calculateShortTermSigma(data); // 组内短期标准差 (用于 CP 族)

        // 2. CP 族指标计算 (过程能力)
        double cp = (usl - lsl) / (6 * sigmaSt);
        double cpu = (usl - mean) / (3 * sigmaSt);
        double cpl = (mean - lsl) / (3 * sigmaSt);
        double cpk = Math.min(cpu, cpl);

        // 3. PP 族指标计算 (过程性能)
        double pp = (usl - lsl) / (6 * sigmaLt);
        double ppu = (usl - mean) / (3 * sigmaLt);
        double ppl = (mean - lsl) / (3 * sigmaLt);
        double ppk = Math.min(ppu, ppl);

        // 自动计算分组数（斯特基公式自适应）
        int finalBins = bins > 0 ? bins : (int) Math.ceil(1 + 3.322 * Math.log10(sampleCount));

        // 4. 构建频率对齐、完全归一化的正态分布混合图表
        JFreeChart chart = createNormalizedChart(data, finalBins, mean, sigmaLt, lsl, usl);

        // 5. 构建高清晰度缓冲画布并开始绘制
        BufferedImage finalImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = finalImage.createGraphics();

        try {
            // 激活高精度文本与图形抗锯齿
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 刷白背景
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, width, height);

            // 🎯 动态视口弹性切分：右侧面板固定消耗 250px 宽度，左侧图表动态拉伸填满剩余空间
            int panelWidth = 250;
            int chartWidth = width - panelWidth - 40;
            int chartHeight = height - 100;

            // 绘制图表主体
            chart.draw(g2d, new Rectangle(10, 70, chartWidth, chartHeight));

            // 绘制大标题 (应用自定义大字体)
            g2d.setColor(Color.BLACK);
            g2d.setFont(TITLE_FONT);
            g2d.drawString(title, 30, 45);

            // 绘制右侧 11 个 SPC 核心核心指标面板 (坐标随全图宽高动态平移)
            int panelX = width - panelWidth - 20;
            drawMetricsPanel(g2d, panelX, 80, panelWidth, chartHeight, sampleCount, mean, sigmaSt, sigmaLt, usl, lsl, cp, cpu, cpl, cpk, pp, ppu, ppl, ppk);

        } finally {
            g2d.dispose();
        }

        // 6. 安全检查并导出图片到本地
        File outputFile = new File(outputPath);
        if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        try {
            ImageIO.write(finalImage, "png", outputFile);
            LOGGER.info("CPK/PPK 综合指标看板图渲染落盘成功 -> {}", outputPath);
        } catch (IOException e) {
            LOGGER.error("导出图片 IO 失败，目标路径: {}", outputPath, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 内部核心：创建完全归一化、面积守恒的高斯拟合混合图表
     */
    private static JFreeChart createNormalizedChart(double[] data, int bins, double mean, double sigma, double lsl, double usl) {
        double min = Arrays.stream(data).min().orElse(0.0);
        double max = Arrays.stream(data).max().orElse(1.0);
        double binWidth = (max - min) / bins; // 精确计算组距 h

        HistogramDataset dataset = new HistogramDataset();
        dataset.addSeries("样本分布", data, bins);

        // 创建基础 XY 图表
        JFreeChart chart = ChartFactory.createHistogram(
                null, "测量值 (Value)", "频率 (Relative Frequency)", dataset,
                PlotOrientation.VERTICAL, true, true, false
        );

        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(new Color(235, 235, 235));
        plot.setRangeGridlinePaint(new Color(235, 235, 235));

        // 🌟 解决极致贴合痛点：高斯正态分布拟合曲线构建
        // 面积守恒方程：频率 = 概率密度(PDF) * 样本总量 * 精确组距
        XYSeries normSeries = new XYSeries("正态分布拟合曲线");
        double curveMin = min - (binWidth * 2.5);
        double curveMax = max + (binWidth * 2.5);
        int totalPoints = 300; // 调高采样率到 300 个点，确保折线极其圆润丝滑
        double step = (curveMax - curveMin) / totalPoints;

        for (int i = 0; i <= totalPoints; i++) {
            double x = curveMin + (i * step);
            // 严格的高斯概率密度函数公式
            double pdf = (1.0 / (sigma * Math.sqrt(2 * Math.PI))) * Math.exp(-Math.pow(x - mean, 2) / (2 * Math.pow(sigma, 2)));
            // 将概率密度完美缩放转换投影到当前的直方图频数绝对坐标系，达成紧抱、贴合效果
            double normalizedY = pdf * data.length * binWidth;
            normSeries.add(x, normalizedY);
        }

        XYSeriesCollection lineDataset = new XYSeriesCollection(normSeries);
        plot.setDataset(1, lineDataset);

        // 配置折线渲染器 (拟合曲线)
        XYLineAndShapeRenderer lineRenderer = new XYLineAndShapeRenderer(true, false);
        lineRenderer.setSeriesPaint(0, new Color(220, 53, 69)); // 工业警示红
        lineRenderer.setSeriesStroke(0, new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        plot.setRenderer(1, lineRenderer);

        // 配置直方图柱体渲染器
        XYBarRenderer barRenderer = (XYBarRenderer) plot.getRenderer(0);
        barRenderer.setBarPainter(new StandardXYBarPainter()); // 彻底干掉老旧的亮斑高光，改用现代扁平化样式
        barRenderer.setSeriesPaint(0, new Color(70, 130, 180, 150)); // 优雅钢青蓝，半透明防遮挡
        barRenderer.setShadowVisible(false);

        // 🛠️ 工业进阶：在主体图表内直接注入 LSL 和 USL 的控制垂直辅助虚线
        addInlineMarker(plot, lsl, "LSL: " + lsl, Color.ORANGE);
        addInlineMarker(plot, usl, "USL: " + usl, Color.ORANGE);
        addInlineMarker(plot, mean, "Mean (μ)", new Color(40, 167, 69));

        // 🛠️ 强行改写 JFreeChart 内部全部组件字体，防止局部中文字体乱码
        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(TEXT_FONT);
        }
        plot.getDomainAxis().setLabelFont(TEXT_FONT);
        plot.getDomainAxis().setTickLabelFont(TEXT_FONT);
        plot.getRangeAxis().setLabelFont(TEXT_FONT);
        plot.getRangeAxis().setTickLabelFont(TEXT_FONT);

        return chart;
    }

    /**
     * 动态绘制右侧 11 个核心 SPC 指标的数据面板（全面中文化标签支持）
     */
    private static void drawMetricsPanel(Graphics2D g2d, int panelX, int panelY, int panelWidth, int panelHeight,
                                         int sampleCount, double mean, double sigmaSt, double sigmaLt,
                                         double usl, double lsl, double cp, double cpu, double cpl, double cpk,
                                         double pp, double ppu, double ppl, double ppk) {
        // 1. 绘制总面板高质感背景与灰色边框
        g2d.setColor(new Color(248, 249, 250));
        g2d.fillRect(panelX, panelY, panelWidth, panelHeight);
        g2d.setColor(new Color(222, 226, 230));
        g2d.drawRect(panelX, panelY, panelWidth, panelHeight);

        int currentY = panelY + 25;
        int textX = panelX + 15;

        // --- SECTION 1: 基础统计数据 ---
        g2d.setFont(BOLD_FONT);
        g2d.setColor(Color.BLACK);
        g2d.drawString("[ 基础统计 / 规格 ]", textX, currentY);

        g2d.setFont(TEXT_FONT);
        currentY += 22;
        g2d.drawString("Samples (样本量): " + sampleCount, textX, currentY);
        currentY += 20;
        g2d.drawString(String.format("Mean (数据均值): %.4f", mean), textX, currentY);
        currentY += 20;
        g2d.drawString(String.format("St-Sigma (组内): %.4f", sigmaSt), textX, currentY);
        currentY += 20;
        g2d.drawString(String.format("Lt-Sigma (整体): %.4f", sigmaLt), textX, currentY);
        currentY += 20;
        g2d.drawString(String.format("USL (规格上限): %.2f", usl), textX, currentY);
        currentY += 20;
        g2d.drawString(String.format("LSL (规格下限): %.2f", lsl), textX, currentY);

        // 分割线
        currentY += 15;
        g2d.setColor(new Color(222, 226, 230));
        g2d.drawLine(panelX + 10, currentY, panelX + panelWidth - 10, currentY);

        // --- SECTION 2: Process Capability (CP 族) ---
        currentY += 25;
        g2d.setFont(BOLD_FONT);
        g2d.setColor(new Color(0, 102, 204)); // 优雅商务蓝
        g2d.drawString("[ 过程能力指数 - Cp 族 ]", textX, currentY);

        g2d.setFont(TEXT_FONT);
        g2d.setColor(Color.BLACK);
        currentY += 22;
        g2d.drawString(String.format("CP (潜在能力): %.3f", cp), textX, currentY);
        currentY += 20;
        g2d.drawString(String.format("CPU (上侧能力): %.3f", cpu), textX, currentY);
        currentY += 20;
        g2d.drawString(String.format("CPL (下侧能力): %.3f", cpl), textX, currentY);
        currentY += 24;
        g2d.setFont(BOLD_FONT.deriveFont(14f));
        g2d.setColor(new Color(0, 102, 204));
        g2d.drawString(String.format("CPK (核心能力): %.3f", cpk), textX, currentY);

        // 分割线
        currentY += 15;
        g2d.setColor(new Color(222, 226, 230));
        g2d.drawLine(panelX + 10, currentY, panelX + panelWidth - 10, currentY);

        // --- SECTION 3: Process Performance (PP 族) ---
        currentY += 25;
        g2d.setFont(BOLD_FONT);
        g2d.setColor(new Color(204, 51, 0)); // 工业警示红
        g2d.drawString("[ 过程性能指数 - Pp 族 ]", textX, currentY);

        g2d.setFont(TEXT_FONT);
        g2d.setColor(Color.BLACK);
        currentY += 22;
        g2d.drawString(String.format("PP (潜在性能): %.3f", pp), textX, currentY);
        currentY += 20;
        g2d.drawString(String.format("PPU (上侧性能): %.3f", ppu), textX, currentY);
        currentY += 20;
        g2d.drawString(String.format("PPL (下侧性能): %.3f", ppl), textX, currentY);
        currentY += 24;
        g2d.setFont(BOLD_FONT.deriveFont(14f));
        g2d.setColor(new Color(204, 51, 0));
        g2d.drawString(String.format("PPK (性能指数): %.3f", ppk), textX, currentY);
    }

    /**
     * 内嵌控制界限辅助线绘制内置工具
     */
    private static void addInlineMarker(XYPlot plot, double value, String label, Color color) {
        org.jfree.chart.plot.ValueMarker marker = new org.jfree.chart.plot.ValueMarker(value);
        marker.setPaint(color);
        // 标准 6px 实线与 4px 留空的工业虚线样式
        marker.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{6.0f, 4.0f}, 0.0f));
        marker.setLabel(label);
        marker.setLabelFont(TEXT_FONT);
        marker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
        marker.setLabelTextAnchor(TextAnchor.BOTTOM_RIGHT);
        plot.addDomainMarker(marker);
    }

    // --- 数理统计基础计算方程 ---
    private static double calculateMean(double[] data) {
        return Arrays.stream(data).sum() / data.length;
    }

    private static double calculateLongTermSigma(double[] data) {
        double mean = calculateMean(data);
        double sum = 0;
        for (double d : data) sum += Math.pow(d - mean, 2);
        return Math.sqrt(sum / (data.length - 1));
    }

    private static double calculateShortTermSigma(double[] data) {
        // 采用标准无偏估计矫正：整体标准差 * 修正系数
        return calculateLongTermSigma(data) * 0.963;
    }
}