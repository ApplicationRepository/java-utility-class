package com.uc.chart;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * 散点图生成工具类
 */
public final class ScatterChartUtils {

    private ScatterChartUtils() {
    }

    /**
     * 1. 生成【标准布局】的散点图（坐标轴默认在最下方和最左侧，适合纯正数数据，如工龄与薪资）
     *
     * @param title      图表大标题
     * @param xAxisLabel X 轴标签
     * @param yAxisLabel Y 轴标签
     * @param dataset    散点数据集
     * @param outputPath 图片输出路径
     * @param width      图片宽度
     * @param height     图片高度
     */
    public static void processStandardChart(String title, String xAxisLabel, String yAxisLabel, XYSeriesCollection dataset, String outputPath, int width, int height) {
        // 创建标准散点图
        JFreeChart chart = ChartFactory.createScatterPlot(title, xAxisLabel, yAxisLabel, dataset, PlotOrientation.VERTICAL, true, true, false);

        XYPlot plot = (XYPlot) chart.getPlot();

        // 应用基础样式与字体美化
        applyBaseStyle(plot, chart);

        // 保存为图片
        saveChartAsPng(chart, outputPath, width, height);
    }

    /**
     * 2. 生成【十字象限居中布局】的散点图（(0,0)原点绝对居中，适合有正有负的偏差、对齐度分析）
     *
     * @param title      图表大标题
     * @param xAxisLabel X 轴标签
     * @param yAxisLabel Y 轴标签
     * @param dataset    散点数据集
     * @param outputPath 图片输出路径
     * @param width      图片宽度
     * @param height     图片高度
     */
    public static void processCenterQuadrantChart(String title, String xAxisLabel, String yAxisLabel, XYSeriesCollection dataset, String outputPath, int width, int height) {
        JFreeChart chart = ChartFactory.createScatterPlot(title, xAxisLabel, yAxisLabel, dataset, PlotOrientation.VERTICAL, true, true, false);

        XYPlot plot = (XYPlot) chart.getPlot();

        // 1. 应用背景及散点基础美化
        applyBaseStyle(plot, chart);

        // 2. 开启并加粗零度中心基准十字线
        plot.setDomainZeroBaselineVisible(true);
        plot.setRangeZeroBaselineVisible(true);
        plot.setDomainZeroBaselinePaint(Color.DARK_GRAY);
        plot.setRangeZeroBaselinePaint(Color.DARK_GRAY);
        plot.setDomainZeroBaselineStroke(new BasicStroke(2.0f));
        plot.setRangeZeroBaselineStroke(new BasicStroke(2.0f));

        // 3. 动态计算对称区间，强行原点居中
        double maxAbsX = 0;
        double maxAbsY = 0;
        for (int s = 0; s < dataset.getSeriesCount(); s++) {
            for (int i = 0; i < dataset.getItemCount(s); i++) {
                double x = dataset.getXValue(s, i);
                double y = dataset.getYValue(s, i);
                if (Math.abs(x) > maxAbsX) maxAbsX = Math.abs(x);
                if (Math.abs(y) > maxAbsY) maxAbsY = Math.abs(y);
            }
        }

        // 边缘留出 20% 的安全空隙
        double xBound = maxAbsX == 0 ? 10 : Math.ceil(maxAbsX * 1.2);
        double yBound = maxAbsY == 0 ? 10 : Math.ceil(maxAbsY * 1.2);

        NumberAxis xAxis = (NumberAxis) plot.getDomainAxis();
        xAxis.setRange(-xBound, xBound);

        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setRange(-yBound, yBound);

        // ==========================================
        // 🔥 核心改动：隐藏四周默认刻度，手动将刻度画在中间
        // ==========================================
        xAxis.setTickLabelsVisible(false); // 隐藏最下方的 X 轴数字
        yAxis.setTickLabelsVisible(false); // 隐藏最左侧的 Y 轴数字

        Font tickFont = new Font("Arial", Font.PLAIN, 11);
        Color tickColor = Color.GRAY;

        // 4. 手动生成并添加 X 轴中间的刻度文字 (每隔一定步长画一个数字)
        double xStep = xBound / 4; // 左右各分4个主刻度
        for (double x = -xBound + xStep; x < xBound; x += xStep) {
            if (Math.abs(x) < 0.001) {
                continue; // 跳过原点 0
            }

            // 创建文本注解：参数为 (显示的文字, X坐标, Y坐标)
            XYTextAnnotation anno = new XYTextAnnotation(String.format("%.1f", x), x, -yBound * 0.04);
            anno.setFont(tickFont);
            anno.setPaint(tickColor);
            anno.setTextAnchor(TextAnchor.TOP_CENTER); // 文字对齐方式居中偏下
            plot.addAnnotation(anno);
        }

        // 5. 手动生成并添加 Y 轴中间的刻度文字
        double yStep = yBound / 4; // 上下各分4个主刻度
        for (double y = -yBound + yStep; y < yBound; y += yStep) {
            if (Math.abs(y) < 0.001) {
                continue;// 跳过原点 0
            }

            // 将文字稍微往左偏移一点 (x = -xBound * 0.02)，防止被纵向主轴线挡住
            XYTextAnnotation anno = new XYTextAnnotation(String.format("%.1f", y), -xBound * 0.02, y);
            anno.setFont(tickFont);
            anno.setPaint(tickColor);
            anno.setTextAnchor(TextAnchor.CENTER_RIGHT); // 文字靠右对齐
            plot.addAnnotation(anno);
        }

        // 6. 额外补一个中心原点 "0"
        XYTextAnnotation zeroAnno = new XYTextAnnotation("0", -xBound * 0.02, -yBound * 0.04);
        zeroAnno.setFont(tickFont);
        zeroAnno.setPaint(tickColor);
        zeroAnno.setTextAnchor(TextAnchor.TOP_RIGHT);
        plot.addAnnotation(zeroAnno);

        // 保存为图片
        saveChartAsPng(chart, outputPath, width, height);
    }

    // --- 内部公共美化逻辑抽取 ---
    private static void applyBaseStyle(XYPlot plot, JFreeChart chart) {
        // 背景与网格线
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(new Color(230, 230, 230));
        plot.setRangeGridlinePaint(new Color(230, 230, 230));

        // 散点渲染器控制：隐藏线，只留点
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(false, true);
        renderer.setSeriesPaint(0, new Color(0, 0, 255)); // 默认给个大气的砖红色
        renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-3.5, -3.5, 7.0, 7.0)); // 统一散点大小
        plot.setRenderer(renderer);

        // 防止中文乱码的字体设置
        Font titleFont = new Font("Microsoft YaHei", Font.BOLD, 18);
        Font labelFont = new Font("Microsoft YaHei", Font.PLAIN, 13);
        Font tickFont = new Font("Arial", Font.PLAIN, 12);

        chart.getTitle().setFont(titleFont);
        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(labelFont);
        }

        NumberAxis xAxis = (NumberAxis) plot.getDomainAxis();
        xAxis.setLabelFont(labelFont);
        xAxis.setTickLabelFont(tickFont);

        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setLabelFont(labelFont);
        yAxis.setTickLabelFont(tickFont);
    }

    // --- 内部图片输出逻辑抽取 ---
    private static void saveChartAsPng(JFreeChart chart, String outputPath, int width, int height) {
        File outputFile = new File(outputPath);
        if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        try {
            ChartUtils.saveChartAsPNG(outputFile, chart, width, height);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}