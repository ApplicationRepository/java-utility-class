package com.uc.pptx;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTAxDataSource;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTNumDataSource;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTScatterChart;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTScatterSer;

import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Log4j2
public final class PptxNormalDistUtils {

    private PptxNormalDistUtils() {
    }

    public static void process(XMLSlideShow ppt, Map<String, NormalDistConfig> configMap, String placeholderMark) {
        if (ppt == null || configMap == null || configMap.isEmpty()) {
            return;
        }

        for (XSLFSlide slide : ppt.getSlides()) {
            Map<XSLFTextShape, NormalDistConfig> tasks = new HashMap<>();

            // 1. 扫描占位符
            scanPlaceholders(slide, configMap, placeholderMark, tasks);
            LOGGER.info("扫描到的正态分布图任务数量：{}", tasks.size());

            // 2. 渲染原生正态分布图
            tasks.forEach((shape, config) -> {
                try {
                    createNormalDistributionChart(ppt, slide, shape, config);
                } catch (Exception e) {
                    LOGGER.error("生成正态分布图失败", e);
                }
            });
        }
        LOGGER.info("正态分布图生成完毕");
    }

    private static void createNormalDistributionChart(XMLSlideShow ppt, XSLFSlide slide, XSLFTextShape textShape, NormalDistConfig config) throws Exception {
        Rectangle2D anchor = textShape.getAnchor();
        if (anchor == null) {
            return;
        }

        // 1. 创建图表对象并将其绑定到幻灯片指定位置
        XSLFChart chart = ppt.createChart();
        slide.addChart(chart, anchor);

        // 2. 创建数值双坐标轴（散点图必须是数值轴）
        XDDFValueAxis xAxis = chart.createValueAxis(AxisPosition.BOTTOM);
        xAxis.setTitle("数据区间");
        XDDFValueAxis yAxis = chart.createValueAxis(AxisPosition.LEFT);
        yAxis.setTitle("概率密度");

        // 3. 初始化底层的 Excel 工作簿
        XSSFWorkbook workbook = chart.getWorkbook();
        XSSFSheet sheet = workbook.getSheetAt(0);
        if (sheet == null) {
            sheet = workbook.createSheet("Sheet1");
        }

        // 创建 Excel 表头
        XSSFRow headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("X-Value");
        headerRow.createCell(1).setCellValue(config.getSeriesTitle());

        // 4. 生成 100 个正态分布数据点并写入隐藏的 Excel
        int pointsCount = 100;
        double minX = config.getMean() - 3.5 * config.getStandardDeviation();
        double maxX = config.getMean() + 3.5 * config.getStandardDeviation();
        double step = (maxX - minX) / (pointsCount - 1);

        for (int i = 0; i < pointsCount; i++) {
            double x = minX + i * step;
            double y = calculateNormalDistributionY(x, config.getMean(), config.getStandardDeviation());

            XSSFRow dataRow = sheet.createRow(i + 1);
            dataRow.createCell(0).setCellValue(x);
            dataRow.createCell(1).setCellValue(y);
        }

        // 5. 创建数据区域绑定引用
        CellRangeAddress xRange = new CellRangeAddress(1, pointsCount, 0, 0);
        CellRangeAddress yRange = new CellRangeAddress(1, pointsCount, 1, 1);

        // 从 Excel 区域提取 XDDF 数据源
        XDDFDataSource<Double> xs = XDDFDataSourcesFactory.fromNumericCellRange(sheet, xRange);
        XDDFNumericalDataSource<Double> ys = XDDFDataSourcesFactory.fromNumericCellRange(sheet, yRange);

        // 6. 指定图表类型为散点图（SCATTER）
        XDDFChartData data = chart.createData(ChartTypes.SCATTER, xAxis, yAxis);

        // 7. 添加数据序列
        XDDFChartData.Series series = data.addSeries(xs, ys);
        CellReference titleRef = new CellReference(sheet.getSheetName(), 0, 1, true, true);
        series.setTitle(config.getSeriesTitle(), titleRef);

        // 8. 强制美化曲线：开启平滑贝塞尔，隐藏散点标记圆点
        if (series instanceof XDDFScatterChartData.Series) {
            XDDFScatterChartData.Series scatterSeries = (XDDFScatterChartData.Series) series;
            scatterSeries.setSmooth(true);
            scatterSeries.setMarkerStyle(MarkerStyle.NONE);
        }

        // 9. 执行 XDDF 高层框架绘制
        chart.plot(data);

        // ====================================================================
        // 🔥【终极修复：100% 解决隐形问题的真正 OpenXML 视图渲染刷新代码】
        // ====================================================================
        // 通过高层散点图对象拿到 OpenXML 规范的 CTScatterChart
        CTScatterChart ctScatterChart = chart.getCTChartSpace().getChart().getPlotArea().getScatterChartArray(0);
        if (ctScatterChart != null && ctScatterChart.sizeOfSerArray() > 0) {
            CTScatterSer ser = ctScatterChart.getSerArray(0);

            // 关键：强制把我们写到 Excel 里的数据区域公式刷新硬写入底层的 XML 视图节点中
            CTAxDataSource xVal = ser.getXVal();
            if (xVal != null && xVal.isSetNumRef()) {
                xVal.getNumRef().setF(xRange.formatAsString("Sheet1", true));
            }

            CTNumDataSource yVal = ser.getYVal();
            if (yVal != null && yVal.isSetNumRef()) {
                yVal.getNumRef().setF(yRange.formatAsString("Sheet1", true));
            }
        }
        // ====================================================================

        // 10. 安全移除原有的文本框占位符
        removeShape(slide, textShape);
    }

    private static double calculateNormalDistributionY(double x, double mean, double stdev) {
        double exponent = Math.exp(-Math.pow(x - mean, 2) / (2 * Math.pow(stdev, 2)));
        return (1.0 / (stdev * Math.sqrt(2 * Math.PI))) * exponent;
    }

    private static void scanPlaceholders(XSLFShapeContainer container, Map<String, NormalDistConfig> configMap, String mark, Map<XSLFTextShape, NormalDistConfig> tasks) {
        for (XSLFShape shape : container.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                XSLFTextShape textShape = (XSLFTextShape) shape;
                String fullText = textShape.getText();
                if (fullText == null || fullText.isEmpty()) {
                    continue;
                }

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
            if (shape instanceof XSLFGroupShape) {
                removeShape((XSLFGroupShape) shape, target);
            }
        }
    }

    @Getter
    public static class NormalDistConfig implements Serializable {
        private static final long serialVersionUID = 842478687215701952L;
        private final String seriesTitle;
        private final double mean;
        private final double standardDeviation;

        public NormalDistConfig(String seriesTitle, double mean, double standardDeviation) {
            this.seriesTitle = seriesTitle;
            this.mean = mean;
            this.standardDeviation = standardDeviation;
        }
    }
}