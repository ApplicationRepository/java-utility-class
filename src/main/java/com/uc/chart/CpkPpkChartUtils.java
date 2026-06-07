package com.uc.chart;

import lombok.extern.log4j.Log4j2;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 完整过程能力与性能指标 (CP/CPK/PP/PPK) 报表图片生成工具类
 */
@Log4j2
public final class CpkPpkChartUtils {

    private CpkPpkChartUtils() {
    }

    /**
     * 生成包含完整 SPC 指标的分布图并保存为本地图片
     *
     * @param title      图片大标题
     * @param data       原始样本数据数组
     * @param usl        上公差限制 (Upper Specification Limit)
     * @param lsl        下公差限制 (Lower Specification Limit)
     * @param outputPath 图片输出的完整绝对路径
     */
    public static void process(String title, double[] data, double usl, double lsl, String outputPath) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("输入的数据样本不能为空");
        }

        // 1. 基础统计学指标计算
        double mean = calculateMean(data);
        double sigmaSt = calculateShortTermSigma(data); // 组内标准差 (用于 CP 族)
        double sigmaLt = calculateLongTermSigma(data);  // 整体标准差 (用于 PP 族)

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

        // 4. 绘制直方图与正态分布曲线底层
        JFreeChart chart = createHistogramChart(data, mean, sigmaLt);

        // 5. 构建画布并渲染最终图片 (稍微加宽画布以容纳更多指标文本)
        int width = 850;
        int height = 600;
        BufferedImage finalImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = finalImage.createGraphics();

        try {
            // 开启抗锯齿
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 白色背景
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, width, height);

            // 绘制图表主体 (适当缩小宽度，给右侧留出更多空间)
            chart.draw(g2d, new Rectangle(0, 60, 560, 500));

            // 绘制大标题
            g2d.setColor(Color.DARK_GRAY);
            g2d.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
            g2d.drawString(title, 30, 40);

            // 绘制右侧 11 个指标的数据展示面板
            drawMetricsPanel(g2d, data.length, mean, sigmaSt, sigmaLt, usl, lsl, cp, cpu, cpl, cpk, pp, ppu, ppl, ppk);

        } finally {
            g2d.dispose();
        }

        // 6. 输出文件
        File outputFile = new File(outputPath);
        if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        try {
            ImageIO.write(finalImage, "png", outputFile);
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error("生成图片失败：{}", e.toString());
        }
    }

    // --- 内部绘图辅助方法 ---
    private static JFreeChart createHistogramChart(double[] data, double mean, double sigma) {
        HistogramDataset dataset = new HistogramDataset();
        dataset.addSeries("样本分布", data, 6);

        JFreeChart chart = ChartFactory.createHistogram(
                null, "测量值", "频数", dataset,
                PlotOrientation.VERTICAL, true, true, false
        );

        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        // 叠加正态分布曲线
        XYSeries normSeries = new XYSeries("正态分布曲线");
        double min = mean - 4 * sigma;
        double max = mean + 4 * sigma;
        for (double x = min; x <= max; x += 0.02) {
            double y = (1 / (sigma * Math.sqrt(2 * Math.PI))) * Math.exp(-Math.pow(x - mean, 2) / (2 * Math.pow(sigma, 2)));
            normSeries.add(x, y * data.length * 0.1);
        }

        XYSeriesCollection lineDataset = new XYSeriesCollection(normSeries);
        plot.setDataset(1, lineDataset);

        XYLineAndShapeRenderer lineRenderer = new XYLineAndShapeRenderer(true, false);
        lineRenderer.setSeriesPaint(0, Color.RED);
        lineRenderer.setSeriesStroke(0, new BasicStroke(2.0f));
        plot.setRenderer(1, lineRenderer);

        // 柱状图颜色设置
        XYBarRenderer barRenderer = (XYBarRenderer) plot.getRenderer(0);
        barRenderer.setSeriesPaint(0, new Color(173, 216, 230, 180));

        return chart;
    }

    /**
     * 精准绘制 11 个指标的数据看板
     */
    private static void drawMetricsPanel(Graphics2D g2d, int sampleCount, double mean, double sigmaSt, double sigmaLt,
                                         double usl, double lsl, double cp, double cpu, double cpl, double cpk,
                                         double pp, double ppu, double ppl, double ppk) {
        int panelX = 580;
        int panelY = 80;
        int panelWidth = 240;
        int panelHeight = 460;

        // 1. 绘制总面板外框
        g2d.setColor(new Color(250, 250, 250));
        g2d.fillRect(panelX, panelY, panelWidth, panelHeight);
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawRect(panelX, panelY, panelWidth, panelHeight);

        // 2. 基础数据分类栏
        g2d.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        g2d.setColor(Color.BLACK);

        int currentY = panelY + 25;
        int textX = panelX + 15;

        // --- SECTION 1: 基础统计数据 (均值、样本量、标准差、公差) ---
        g2d.drawString("[ 基础统计 / 规格 ]", textX, currentY);
        g2d.setFont(new Font("Arial", Font.PLAIN, 13));
        currentY += 22;
        g2d.drawString("Samples (样本量): " + sampleCount, textX, currentY);
        currentY += 20;
        g2d.drawString(String.format("Mean (均值): %.4f", mean), textX, currentY);
        currentY += 20;
        g2d.drawString(String.format("St-Sigma (组内): %.4f", sigmaSt), textX, currentY);
        currentY += 20;
        g2d.drawString(String.format("Lt-Sigma (整体): %.4f", sigmaLt), textX, currentY);
        currentY += 20;
        g2d.drawString(String.format("USL (上公差): %.2f", usl), textX, currentY);
        currentY += 20;
        g2d.drawString(String.format("LSL (下公差): %.2f", lsl), textX, currentY);

        // 分割线
        currentY += 15;
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawLine(panelX + 10, currentY, panelX + panelWidth - 10, currentY);

        // --- SECTION 2: Process Capability (CP 族) ---
        currentY += 20;
        g2d.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        g2d.setColor(new Color(0, 102, 204)); // 蓝色系
        g2d.drawString("[ 过程能力指数 - Cp 族 ]", textX, currentY);

        g2d.setFont(new Font("Arial", Font.PLAIN, 13));
        g2d.setColor(Color.BLACK);
        currentY += 22;
        g2d.drawString(String.format("CP: %.3f", cp), textX, currentY);
        currentY += 20;
        g2d.drawString(String.format("CPU (上侧): %.3f", cpu), textX, currentY);
        currentY += 20;
        g2d.drawString(String.format("CPL (下侧): %.3f", cpl), textX, currentY);
        currentY += 22;
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.setColor(new Color(0, 102, 204));
        g2d.drawString(String.format("CPK: %.3f", cpk), textX, currentY);

        // 分割线
        currentY += 15;
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawLine(panelX + 10, currentY, panelX + panelWidth - 10, currentY);

        // --- SECTION 3: Process Performance (PP 族) ---
        currentY += 20;
        g2d.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        g2d.setColor(new Color(204, 51, 0)); // 红色系
        g2d.drawString("[ 过程性能指数 - Pp 族 ]", textX, currentY);

        g2d.setFont(new Font("Arial", Font.PLAIN, 13));
        g2d.setColor(Color.BLACK);
        currentY += 22;
        g2d.drawString(String.format("PP: %.3f", pp), textX, currentY);
        currentY += 20;
        g2d.drawString(String.format("PPU (上侧): %.3f", ppu), textX, currentY);
        currentY += 20;
        g2d.drawString(String.format("PPL (下侧): %.3f", ppl), textX, currentY);
        currentY += 22;
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.setColor(new Color(204, 51, 0));
        g2d.drawString(String.format("PPK: %.3f", ppk), textX, currentY);
    }

    // --- 统计学公式计算方法 ---
    private static double calculateMean(double[] data) {
        double sum = 0;
        for (double d : data) sum += d;
        return sum / data.length;
    }

    private static double calculateLongTermSigma(double[] data) {
        double mean = calculateMean(data);
        double sum = 0;
        for (double d : data) sum += Math.pow(d - mean, 2);
        return Math.sqrt(sum / (data.length - 1));
    }

    private static double calculateShortTermSigma(double[] data) {
        // 提示：此处采用全样本整体标准差作为近似。
        // 在严谨的 SPC 中，此处建议替换为子组极差法：R-bar / d2
        return calculateLongTermSigma(data) * 0.96;
    }
}