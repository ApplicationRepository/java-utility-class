package com.uc.pptx.processor;

import cn.hutool.core.util.StrUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xslf.usermodel.*;

import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;

@Log4j2
public class PptxNormalDistProcessor {

    public static void process(XMLSlideShow ppt, Map<String, NormalDistConfig> configMap, String placeholderMark) {
        if (ppt == null || configMap == null || configMap.isEmpty()) return;

        for (XSLFSlide slide : ppt.getSlides()) {
            Map<XSLFTextShape, NormalDistConfig> tasks = new HashMap<>();

            // 1. 扫描占位符
            scanPlaceholders(slide, configMap, placeholderMark, tasks);

            // 2. 渲染原生正态分布图
            tasks.forEach((shape, config) -> {
                try {
                    createNormalDistributionChart(ppt, slide, shape, config);
                } catch (Exception e) {
                    log.error("生成正态分布图失败", e);
                }
            });
        }
    }

    private static void createNormalDistributionChart(XMLSlideShow ppt, XSLFSlide slide, XSLFTextShape textShape, NormalDistConfig config) {
        Rectangle2D anchor = textShape.getAnchor();
        if (anchor == null) return;

        // 1. 先在幻灯片中创建一个图表关系（此时它还是一个没有位置的内存对象）
        XSLFChart chart = ppt.createChart();

        // 2. 【核心修正】调用 slide 的 addChart 方法，同时传入图表和位置（anchor）
        slide.addChart(chart, anchor);

        // 2. 初始化图表的基础布局（散点图/折线图）
        XDDFChartData data = chart.createData(ChartTypes.LINE, null, null);

        // 3. 数学计算：生成正态分布的 X 和 Y 数据集
        int pointsCount = 100; // 采样点数量，越多曲线越平滑
        Double[] xData = new Double[pointsCount];
        Double[] yData = new Double[pointsCount];

        double minX = config.getMean() - 3.5 * config.getStdev();
        double maxX = config.getMean() + 3.5 * config.getStdev();
        double step = (maxX - minX) / (pointsCount - 1);

        for (int i = 0; i < pointsCount; i++) {
            double x = minX + i * step;
            double y = calculateNormalDistributionY(x, config.getMean(), config.getStdev());
            xData[i] = x;
            yData[i] = y;
        }

        // 4. 将数据包装为 XDDF 能够识别的数据源
        XDDFDataSource<Double> xs = XDDFDataSourcesFactory.fromArray(xData);
        XDDFNumericalDataSource<Double> ys = XDDFDataSourcesFactory.fromArray(yData);

        // 5. 将数据集绑定到图表的 Series 中
        XDDFChartData.Series series = data.addSeries(xs, ys);
        series.setTitle(config.getSeriesTitle(), null);

        // 关键设置：使散点图的各点之间通过平滑曲线连接，形成“钟形曲线”
        if (series instanceof XDDFScatterChartData.Series) {
            ((XDDFScatterChartData.Series) series).setSmooth(true);
        }

        // 6. 配置坐标轴
        XDDFValueAxis xAxis = chart.createValueAxis(AxisPosition.BOTTOM);
        xAxis.setTitle("数据区间");
        XDDFValueAxis yAxis = chart.createValueAxis(AxisPosition.LEFT);
        yAxis.setTitle("概率密度");

        // 7. 绘制图表
        chart.plot(data);

        // 8. 移除原文本框占位符
        removeShape(slide, textShape);
    }

    /**
     * 正态分布概率密度函数公式实现
     */
    private static double calculateNormalDistributionY(double x, double mean, double stdev) {
        double exponent = Math.exp(-Math.pow(x - mean, 2) / (2 * Math.pow(stdev, 2)));
        return (1.0 / (stdev * Math.sqrt(2 * Math.PI))) * exponent;
    }

    private static void scanPlaceholders(XSLFShapeContainer container, Map<String, NormalDistConfig> configMap, String mark, Map<XSLFTextShape, NormalDistConfig> tasks) {
        for (XSLFShape shape : container.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                XSLFTextShape textShape = (XSLFTextShape) shape;
                String fullText = textShape.getText();
                if (fullText == null || fullText.isEmpty()) continue;

                for (Map.Entry<String, NormalDistConfig> entry : configMap.entrySet()) {
                    if (StrUtil.containsAnyIgnoreCase(fullText, entry.getKey()) && entry.getKey().contains(mark)) {
                        tasks.put(textShape, entry.getValue());
                        break;
                    }
                }
            } else if (shape instanceof XSLFGroupShape) {
                scanPlaceholders((XSLFGroupShape) shape, configMap, mark, tasks);
            }
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
     * 正态分布配置参数类
     */
    public static class NormalDistConfig {
        private final String seriesTitle; // 曲线名称
        private final double mean;        // 均值 (μ)
        private final double stdev;       // 标准差 (σ)

        public NormalDistConfig(String seriesTitle, double mean, double stdev) {
            this.seriesTitle = seriesTitle;
            this.mean = mean;
            this.stdev = stdev;
        }

        public String getSeriesTitle() {
            return seriesTitle;
        }

        public double getMean() {
            return mean;
        }

        public double getStdev() {
            return stdev;
        }
    }
}