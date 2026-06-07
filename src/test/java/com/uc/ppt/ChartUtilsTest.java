package com.uc.ppt;

import com.uc.chart.CpkPpkChartUtils;
import com.uc.chart.SankeyChartUtils;
import com.uc.chart.ScatterChartUtils;
import com.uc.entity.SankeyEntity;
import lombok.extern.log4j.Log4j2;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Log4j2
public class ChartUtilsTest {

    private static final String BASE_PATH = "/Users/administrator/Desktop/work-space/IDEA/java-utility-class/src/test/java/com/uc/ppt/file";

    @Test
    public void cpkPpkTest() {
        // 1. 准备您的业务流水样本数据
        double[] data = {10.2, 10.1, 10.5, 9.9, 9.8, 10.3, 10.0, 10.2, 10.1, 10.4, 10.0, 9.7, 10.1, 10.3, 10.2, 9.9, 10.0, 10.1, 10.2, 10.0};

        // 2. 设定公差限制
        double usl = 10.5;
        double lsl = 9.5;

        // 3. 定义完整的输出图片路径
        String outputPath = BASE_PATH + "/chart/CPK_PPK.png";
        // 4. 一键调用工具类生成图片
        CpkPpkChartUtils.process("质量特性控制报告 (CPK / PPK)", data, usl, lsl, outputPath);
        LOGGER.info("报表图片已成功生成至：{}", outputPath);

    }

    @Test
    public void standardScatterTest() {
        // 1. 创建数据集并添加散点系列
        XYSeries series = new XYSeries("员工数据样本");

        // 2. 初始化随机数生成器
        Random random = new Random();

        // 定义你想生成的随机数边界（根据你给出的数据特征进行微调）
        double xMin = 1;
        double xMax = 30;
        double yMin = 10;
        double yMax = 100.0;

        // 循环生成 100 组 X 和 Y 坐标点
        for (int i = 0; i < 1000; i++) {
            // 公式：min + (max - min) * random
            double randomX = xMin + (xMax - xMin) * random.nextDouble();
            double randomY = yMin + (yMax - yMin) * random.nextDouble();
            // 将随机点加入到系列中
            series.add(randomX, randomY);
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(series);

        // 2. 定义图片输出路径
        String outputPath = BASE_PATH + "/chart/Standard_Scatter.png";

        // 3. 调用工具类一键生成 800x600 的散点图
        ScatterChartUtils.processStandardChart("工龄与薪资关系分布散点图", "工作年限 (年)", "年薪 (万元)", dataset, outputPath, 800, 600);
        LOGGER.info("散点图图片已成功生成至: {}", outputPath);

    }


    @Test
    public void centerQuadrantScatterTest() {
        // 1. 创建数据集
        XYSeries series = new XYSeries("位移偏差样本");

        // 2. 初始化随机数生成器
        Random random = new Random();

        // 定义你想生成的随机数边界（根据你给出的数据特征进行微调）
        double xMin = -100.0;
        double xMax = 100.0;
        double yMin = -100.0;
        double yMax = 100.0;

        // 循环生成 1000 组 X 和 Y 坐标点
        for (int i = 0; i < 1000; i++) {
            // 公式：min + (max - min) * random
            double randomX = xMin + (xMax - xMin) * random.nextDouble();
            double randomY = yMin + (yMax - yMin) * random.nextDouble();
            // 将随机点加入到系列中
            series.add(randomX, randomY);
        }
        XYSeriesCollection dataset = new XYSeriesCollection(series);
        String path = BASE_PATH + "/chart/Center_Quadrant_Scatter.png";

        // 💥 通过清晰的方法名直观调用中心对称十字版
        ScatterChartUtils.processCenterQuadrantChart("模具成型中心点偏离度象限分析", "X方向偏差 (mm)", "Y方向偏差 (mm)", dataset, path, 800, 600);
        LOGGER.info("十字中心散点图已生成:{}", path);
    }

    @Test
    public void sankeyTest() {
        // 1. 创建实体类列表
        List<SankeyEntity> sankeyEntityList = new ArrayList<>();

        // LEVEL 1 -> LEVEL 2 (总预算 -> 部门分流)
        sankeyEntityList.add(new SankeyEntity("总预算", "研发部", 500.0));
        sankeyEntityList.add(new SankeyEntity("总预算", "市场部", 350.0));
        sankeyEntityList.add(new SankeyEntity("总预算", "行政部", 150.0));

        // LEVEL 2 -> LEVEL 3 (部门分流 -> 费用大类)
        sankeyEntityList.add(new SankeyEntity("研发部", "薪酬支出", 400.0));
        sankeyEntityList.add(new SankeyEntity("研发部", "设备添置", 100.0));
        sankeyEntityList.add(new SankeyEntity("市场部", "广告外包", 250.0));
        sankeyEntityList.add(new SankeyEntity("市场部", "线下物料", 100.0));
        sankeyEntityList.add(new SankeyEntity("行政部", "日常运营", 150.0));

        // LEVEL 3 -> LEVEL 4 (费用大类 -> 具体明细科目)
        sankeyEntityList.add(new SankeyEntity("薪酬支出", "研发A组薪资", 250.0));
        sankeyEntityList.add(new SankeyEntity("薪酬支出", "研发B组薪资", 150.0));
        sankeyEntityList.add(new SankeyEntity("广告外包", "信息流广告", 180.0));
        sankeyEntityList.add(new SankeyEntity("广告外包", "视频号投流", 70.0));
        sankeyEntityList.add(new SankeyEntity("日常运营", "办公房租", 100.0));
        sankeyEntityList.add(new SankeyEntity("日常运营", "水电物业", 50.0));

        //  新增 LEVEL 4 -> LEVEL 5 (明细科目 -> 签署外部供应商/合同方)
        sankeyEntityList.add(new SankeyEntity("研发A组薪资", "外包服务商A", 250.0));
        sankeyEntityList.add(new SankeyEntity("信息流广告", "巨量引擎", 180.0));
        sankeyEntityList.add(new SankeyEntity("视频号投流", "腾讯广告", 70.0));
        sankeyEntityList.add(new SankeyEntity("办公房租", "物业资产公司", 100.0));

        // 新增 LEVEL 5 -> LEVEL 6 (外部供应商 -> 最终财务支付通道/银行)
        sankeyEntityList.add(new SankeyEntity("外包服务商A", "招商银行代发", 250.0));
        sankeyEntityList.add(new SankeyEntity("巨量引擎", "企业支付宝", 180.0));
        sankeyEntityList.add(new SankeyEntity("腾讯广告", "财付通商户号", 70.0));
        sankeyEntityList.add(new SankeyEntity("物业资产公司", "建设银行对公", 100.0));

        // 未下探至第6层的末端节点，为了保持守恒和连贯，将其直接拉出到终点通道
        sankeyEntityList.add(new SankeyEntity("研发B组薪资", "招商银行代发", 150.0));
        sankeyEntityList.add(new SankeyEntity("线下物料", "企业支付宝", 100.0));
        sankeyEntityList.add(new SankeyEntity("设备添置", "建设银行对公", 100.0));
        sankeyEntityList.add(new SankeyEntity("水电物业", "建设银行对公", 50.0));

        String outputPath = BASE_PATH + "/chart/Sankey.png";
        
        SankeyChartUtils.processSankey("企业年度预算分流看板", sankeyEntityList, outputPath, 1000, 800);
        LOGGER.info("类桑基图已安全生成:{}", outputPath);

    }

}
