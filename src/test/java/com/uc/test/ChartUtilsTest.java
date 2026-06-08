package com.uc.test;

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

    private static final String BASE_PATH = "/Users/administrator/Desktop/work-space/IDEA/java-utility-class/src/test/resources";

    @Test
    public void cpkPpkTest() {
        // 1. 模拟 50,000 条大规模高斯分布检测样本
        int dataSize = 50000;
        double[] mockData = new double[dataSize];
        Random random = new Random();

        double targetMean = 10.0;   // 设定零件外径加工设计中心值为 10.0
        double targetSigma = 0.15;  // 设定标准差波动为 0.15

        for (int i = 0; i < dataSize; i++) {
            // 利用高斯伪随机数函数生成符合大数定律的真实工业散布数据
            mockData[i] = targetMean + random.nextGaussian() * targetSigma;
        }

        // 2. 设定合理的工程规格上下限 (LSL / USL)
        double lsl = 9.5;
        double usl = 10.5;

        // 3. 定义本地输出路径（默认生成在当前项目的根线下）
        String outputPath = BASE_PATH + "/chart/CPK_PPK.png";

        // 4. 自定义大尺寸高清晰度画布宽高 (1150 x 720)
        int customWidth = 1920;
        int customHeight = 1080;

        System.out.println("🚀 正在深度处理 50,000 条样本数据，执行拓扑自适应归一化分析...");

        // 5. 一键触发优化后的 process 生成器
        CpkPpkChartUtils.process("汽车核心制动盘厚度尺寸 CPK/PPK 过程能力综合看板 (5万条完美贴合版)", mockData, usl, lsl, 0,             // bins 传 0 激活代码内置的斯特基公式自适应分组
                outputPath, customWidth,   // 注入自定义宽度
                customHeight   // 注入自定义高度
        );

        System.out.println("✅ 看板报表图已高精生成完毕！");
        System.out.println("📂 图片绝对路径: " + outputPath);

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

        // 通过清晰的方法名直观调用中心对称十字版
        ScatterChartUtils.processCenterQuadrantChart("模具成型中心点偏离度象限分析", "X方向偏差 (mm)", "Y方向偏差 (mm)", dataset, path, 800, 600);
        LOGGER.info("十字中心散点图已生成:{}", path);
    }

    @Test
    public void multiLevelDoubleRowSankeyTest() {
        // 1. 构建一个包含 6 个深层拓扑层级的复杂业务数据集
        List<SankeyEntity> dataList = new ArrayList<>();

        // Level 0 -> Level 1 (原材料 到 核心部件)
        dataList.add(new SankeyEntity("特种钢材基础原料", "高强度车身车架", 5000));
        dataList.add(new SankeyEntity("锂矿及稀有金属", "动力电池电芯工艺", 8000));
        dataList.add(new SankeyEntity("硅晶圆及半导体", "车载芯片总成", 3000));

        // Level 1 -> Level 2 (核心部件 到 模块组装)
        dataList.add(new SankeyEntity("高强度车身车架", "底盘悬挂模组", 5000));
        dataList.add(new SankeyEntity("动力电池电芯工艺", "BMS电池包系统", 8000));
        dataList.add(new SankeyEntity("车载芯片总成", "自动驾驶域控制器", 3000));

        // Level 2 -> Level 3 (模块组装 到 整车集成)
        dataList.add(new SankeyEntity("底盘悬挂模组", "纯电动SUV整车线", 4500));
        dataList.add(new SankeyEntity("BMS电池包系统", "纯电动SUV整车线", 7500));
        dataList.add(new SankeyEntity("BMS电池包系统", "高端轿跑整车线", 500));
        dataList.add(new SankeyEntity("自动驾驶域控制器", "高端轿跑整车线", 2500));

        // Level 3 -> Level 4 (整车集成 到 销售渠道 —— 此处将平滑触发跨层折行)
        dataList.add(new SankeyEntity("纯电动SUV整车线", "自营旗舰体验店", 9000));
        dataList.add(new SankeyEntity("纯电动SUV整车线", "线上电商直销", 3000));
        dataList.add(new SankeyEntity("高端轿跑整车线", "自营旗舰体验店", 2000));
        dataList.add(new SankeyEntity("高端轿跑整车线", "海外出口渠道", 1000));

        // Level 4 -> Level 5 (销售渠道 到 售后市场)
        dataList.add(new SankeyEntity("自营旗舰体验店", "终端车主交付", 10500));
        dataList.add(new SankeyEntity("线上电商直销", "终端车主交付", 2800));
        dataList.add(new SankeyEntity("海外出口渠道", "国际售后保障部", 1000));
        dataList.add(new SankeyEntity("自营旗舰体验店", "官方二手车回购", 500));

        // 2. 配置画布尺寸与本地输出绝对路径
        // 建议在多层折行场景下使用 1200x750 以上的宽幅画布，给中央跨层转弯曲线留出足够的纵向空间
        int width = 1250;
        int height = 760;
        String outputPath = BASE_PATH + "/chart/Sankey_MultiLevel_DoubleRow_Result.png";

        // 3. 执行测试渲染流程
        LOGGER.info("开始解析 6 层复杂拓扑网络结构");
        LOGGER.info("检查到总层数 > 4，系统将全自动切分为上下双层排版布局。");

        SankeyChartUtils.processSankey("新能源汽车全生命周期价值流向拓扑图 (Java原生双层折行版)", dataList, outputPath, width, height);
        LOGGER.info("桑基图渲染落盘成功！");
        LOGGER.info("图片输出路径: {}", outputPath);
    }


    @Test
    public void complexMultiLevelSankeyTest() {
        List<SankeyEntity> data = new ArrayList<>();

        // --- 第1层 -> 第2层 (原材料到工厂) ---
        data.add(new SankeyEntity("钢材供应商", "制造一厂", 500.0));
        data.add(new SankeyEntity("铝材供应商", "制造一厂", 300.0));
        data.add(new SankeyEntity("塑料供应商", "制造二厂", 400.0));

        // --- 第2层 -> 第3层 (工厂到组装) ---
        data.add(new SankeyEntity("制造一厂", "车架组装线", 600.0));
        data.add(new SankeyEntity("制造一厂", "动力系统部", 200.0));
        data.add(new SankeyEntity("制造二厂", "内饰安装部", 400.0));

        // --- 第3层 -> 第4层 (组装到检测) ---
        data.add(new SankeyEntity("车架组装线", "总装车间", 600.0));
        data.add(new SankeyEntity("动力系统部", "总装车间", 200.0));
        data.add(new SankeyEntity("内饰安装部", "总装车间", 400.0));

        // --- 第4层 -> 第5层 (总装到分拨 - 此处开始可能触发折行) ---
        data.add(new SankeyEntity("总装车间", "华东分拨中心", 700.0));
        data.add(new SankeyEntity("总装车间", "华南分拨中心", 500.0));

        // --- 第5层 -> 第6层 (分拨到门店) ---
        data.add(new SankeyEntity("华东分拨中心", "上海旗舰店", 400.0));
        data.add(new SankeyEntity("华东分拨中心", "杭州直营店", 300.0));
        data.add(new SankeyEntity("华南分拨中心", "广州精品店", 300.0));
        data.add(new SankeyEntity("华南分拨中心", "深圳中心店", 200.0));

        // 设定输出路径
        String outputPath = BASE_PATH + "/chart/Complex_Sankey_DoubleRow.png";

        // 设定大尺寸画布以容纳多层结构
        int width = 1200;
        int height = 800;

        LOGGER.info("正在生成 6 层级自适应折行桑基图...");

        SankeyChartUtils.processSankey("全球供应链产品流向拓扑分析报告 (多层折行演示)", data, outputPath, width, height);

        LOGGER.info("桑基图生成成功！");
        LOGGER.info("文件路径: {}", outputPath);


    }

}
