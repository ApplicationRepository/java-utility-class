package com.uc.chart;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import lombok.extern.log4j.Log4j2;
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

/** 散点图生成工具类 💡 深度整合版：完美解决 [跨平台中文乱码] + [标准/十字象限布局自适应] */
@Log4j2
public final class ScatterChartUtils {

  // 🚀 核心防乱码优化：定义跨平台安全的自适应字体体系
  private static final Font TEXT_FONT;
  private static final Font BOLD_FONT;
  private static final Font TITLE_FONT;

  static {
    // 1. 获取当前系统支持的所有物理字体名称
    String[] fontNames =
        GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
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

    TEXT_FONT = new Font(targetFontFamily, Font.PLAIN, 12);
    BOLD_FONT = new Font(targetFontFamily, Font.BOLD, 13);
    TITLE_FONT = new Font(targetFontFamily, Font.BOLD, 18);

    LOGGER.info("散点图渲染引擎初始化成功，当前运行平台已自动绑定最适中文字体: {}", targetFontFamily);
  }

  private ScatterChartUtils() {}

  /** 1. 生成【标准布局】的散点图（坐标轴默认在最下方和最左侧，适合纯正数数据） */
  public static void processStandardChart(
      String title,
      String xAxisLabel,
      String yAxisLabel,
      XYSeriesCollection dataset,
      String outputPath,
      int width,
      int height) {
    // 创建标准散点图
    JFreeChart chart =
        ChartFactory.createScatterPlot(
            title, xAxisLabel, yAxisLabel, dataset, PlotOrientation.VERTICAL, true, true, false);

    XYPlot plot = (XYPlot) chart.getPlot();

    // 应用基础样式与字体美化
    applyBaseStyle(plot, chart);

    // 保存为图片
    saveChartAsPng(chart, outputPath, width, height);
  }

  /** 2. 生成【十字象限居中布局】的散点图（(0,0)原点绝对居中，适合有正有负的偏差、对齐度分析） */
  public static void processCenterQuadrantChart(
      String title,
      String xAxisLabel,
      String yAxisLabel,
      XYSeriesCollection dataset,
      String outputPath,
      int width,
      int height) {
    JFreeChart chart =
        ChartFactory.createScatterPlot(
            title, xAxisLabel, yAxisLabel, dataset, PlotOrientation.VERTICAL, true, true, false);

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
        if (Math.abs(x) > maxAbsX) {
          maxAbsX = Math.abs(x);
        }
        if (Math.abs(y) > maxAbsY) {
          maxAbsY = Math.abs(y);
        }
      }
    }

    // 边缘留出 20% 的安全空隙
    double xBound = maxAbsX == 0 ? 10 : Math.ceil(maxAbsX * 1.2);
    double yBound = maxAbsY == 0 ? 10 : Math.ceil(maxAbsY * 1.2);

    NumberAxis xAxis = (NumberAxis) plot.getDomainAxis();
    xAxis.setRange(-xBound, xBound);

    NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
    yAxis.setRange(-yBound, yBound);

    // 隐藏四周默认外部刻度，准备将刻度渲染在内部十字线上
    xAxis.setTickLabelsVisible(false);
    yAxis.setTickLabelsVisible(false);

    Color tickColor = Color.GRAY;

    // 4. 手动生成并添加 X 轴中间的刻度文字 (每隔一定步长画一个数字)
    double xStep = xBound / 4; // 左右各分4个主刻度
    for (double x = -xBound + xStep; x < xBound; x += xStep) {
      if (Math.abs(x) < 0.001) {
        continue; // 跳过原点 0
      }

      XYTextAnnotation anno = new XYTextAnnotation(String.format("%.1f", x), x, -yBound * 0.04);
      anno.setFont(TEXT_FONT); // 🚀 关键点：使用全局跨平台安全字体，防止轴上刻度数字乱码
      anno.setPaint(tickColor);
      anno.setTextAnchor(TextAnchor.TOP_CENTER);
      plot.addAnnotation(anno);
    }

    // 5. 手动生成并添加 Y 轴中间的刻度文字
    double yStep = yBound / 4; // 上下各分4个主刻度
    for (double y = -yBound + yStep; y < yBound; y += yStep) {
      if (Math.abs(y) < 0.001) {
        continue; // 跳过原点 0
      }

      XYTextAnnotation anno = new XYTextAnnotation(String.format("%.1f", y), -xBound * 0.02, y);
      anno.setFont(TEXT_FONT); // 🚀 关键点：使用全局跨平台安全字体
      anno.setPaint(tickColor);
      anno.setTextAnchor(TextAnchor.CENTER_RIGHT);
      plot.addAnnotation(anno);
    }

    // 6. 额外补一个中心原点 "0"
    XYTextAnnotation zeroAnno = new XYTextAnnotation("0", -xBound * 0.02, -yBound * 0.04);
    zeroAnno.setFont(TEXT_FONT); // 🚀 关键点：使用全局跨平台安全字体
    zeroAnno.setPaint(tickColor);
    zeroAnno.setTextAnchor(TextAnchor.TOP_RIGHT);
    plot.addAnnotation(zeroAnno);

    // 保存为图片
    saveChartAsPng(chart, outputPath, width, height);
  }

  // --- 内部公共美化与全局字体强力覆盖逻辑 ---
  private static void applyBaseStyle(XYPlot plot, JFreeChart chart) {
    // 背景与网格线
    plot.setBackgroundPaint(Color.WHITE);
    plot.setDomainGridlinePaint(new Color(230, 230, 230));
    plot.setRangeGridlinePaint(new Color(230, 230, 230));

    // 散点渲染器控制：隐藏线，只留点
    XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(false, true);
    renderer.setSeriesPaint(0, new Color(0, 0, 255)); // 优雅皇家蓝
    renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-3.5, -3.5, 7.0, 7.0)); // 统一散点大小
    plot.setRenderer(renderer);

    // 🚀 全面复写 JFreeChart 内部所有组件字体，防止局部中文字体乱码
    chart.getTitle().setFont(TITLE_FONT);
    if (chart.getLegend() != null) {
      chart.getLegend().setItemFont(TEXT_FONT);
    }

    NumberAxis xAxis = (NumberAxis) plot.getDomainAxis();
    xAxis.setLabelFont(BOLD_FONT);
    xAxis.setTickLabelFont(TEXT_FONT);

    NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
    yAxis.setLabelFont(BOLD_FONT);
    yAxis.setTickLabelFont(TEXT_FONT);
  }

  // --- 内部图片输出逻辑抽取 ---
  private static void saveChartAsPng(JFreeChart chart, String outputPath, int width, int height) {
    File outputFile = new File(outputPath);
    if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
      outputFile.getParentFile().mkdirs();
    }
    try {
      ChartUtils.saveChartAsPNG(outputFile, chart, width, height);
      LOGGER.info("散点图渲染落盘成功 -> {}", outputPath);
    } catch (IOException e) {
      LOGGER.error("散点图导出图片 IO 失败，目标路径: {}", outputPath, e);
      throw new RuntimeException(e);
    }
  }
}
