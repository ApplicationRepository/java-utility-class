package com.uc.ppt;

import cn.hutool.core.io.FileUtil;
import com.uc.pptx.PptxTableUtil;
import com.uc.pptx.PptxTemplateUtil;
import com.uc.pptx.PptxUtil;
import com.uc.pptx.processor.*;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.Before;
import org.junit.Test;

import java.awt.*;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PPTX 模板引擎 - 自动替换文字与图片
 */
@Log4j2
public class PptxUtilTest {

    private static final String BASE_PATH = "/Users/administrator/Desktop/work-space/IDEA/java-utility-class/src/test/java/com/uc/ppt/file";
    private static final String TEMPLATE_PATH = BASE_PATH + "/template.pptx";
    private static final String OUTPUT_PATH = BASE_PATH + "/output.pptx";
    private static final String IMAGE_PATH = BASE_PATH + "/1.png";

    /**
     * Java 8 辅助方法：快速创建单键值对的 Map
     */
    private static Map<String, String> createMap(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    /**
     * Java 8 辅助方法：快速创建桑基图 Link 结构的 Map
     */
    private static Map<String, Object> createLinkMap(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        Map<String, Object> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return map;
    }

    @Before
    public void beforeTest() {
        log.info("正在删除：{}。", OUTPUT_PATH);
        FileUtil.del(OUTPUT_PATH);
        log.info("删除{}完成。", OUTPUT_PATH);
    }

    @Test
    public void createPptx() {
        //1.准备替换数据 (Key 为模板中的占位符)
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("${title}", "2026年度业务报告");
        dataMap.put("${author}", "Gemini AI");
        dataMap.put("${date}", "2026-05-07");
        //2.约定：以 {img 开头的键将被识别为图片路径，替换掉对应的文本框
        dataMap.put("${imgChart1}", IMAGE_PATH);
        //执行处理
        PptxUtil.createPptx(TEMPLATE_PATH, OUTPUT_PATH, dataMap, "${img");
    }

    @Test
    public void createPptxTableTest() {
        String TEMPLATE_PATH = BASE_PATH + "/template.pptx";
        String OUTPUT_PATH = BASE_PATH + "/output.pptx";
        // 准备你的业务数据
        String[][] tableData = {{"产品名称", "销量", "销售额"}, {"手机", "100", "¥500,000"}, {"电脑", "50", "¥400,000"}, {"手表", "200", "¥200,000"}};
        try (FileInputStream fis = new FileInputStream(TEMPLATE_PATH); XMLSlideShow ppt = new XMLSlideShow(fis)) {
            // 调用方式 1：使用默认样式（浅蓝表头、黑边框、30行高）
            PptxTableUtil.replacePlaceholderWithTable(ppt, "${tableData}", tableData);
            // 调用方式 2：如果需要自定义高级样式（例如：深灰表头、灰色细边框、35行高）
            Color customHeader = new Color(64, 64, 64);
            Color customBorder = Color.BLUE;
            PptxTableUtil.replacePlaceholderWithTable(ppt, "${tableData}", tableData, customHeader, customBorder, 0.75, 35);
            // 保存文件
            try (FileOutputStream fos = new FileOutputStream(OUTPUT_PATH)) {
                ppt.write(fos);
            }
            log.info("PPT表格替换完成！");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void createPptxTemplateTest() {
        // 1. 准备普通文本数据
        Map<String, String> textMap = new HashMap<>();
        textMap.put("${title}", "2026年度财务报表分析");
        textMap.put("${author}", "张三");
        // 2. 准备图片数据 (占位符建议包含特定前缀)
        Map<String, String> imageMap = new HashMap<>();
        imageMap.put("${imgChart1}", BASE_PATH + "/1.png");
        // 3. 准备表格数据 (占位符建议包含特定前缀)
        Map<String, String[][]> tableMap = new HashMap<>();
        String[][] tableData = {{"项目", "预算", "实际花费"}, {"研发部", "¥500,000", "¥480,000"}, {"市场部", "¥300,000", "¥310,000"}};
        tableMap.put("${tableData}", tableData);
        // 4. 调用合并后的工具类
        PptxTemplateUtil.createPptx(BASE_PATH + "/template.pptx", BASE_PATH + "/output.pptx", textMap, imageMap, tableMap, "${img", "${table");
    }

    @Test
    public void generatePptxTest() throws Exception {
        try (FileInputStream fis = new FileInputStream(TEMPLATE_PATH); XMLSlideShow ppt = new XMLSlideShow(fis)) {
            Map<String, String> textMap = new HashMap<>();
            textMap.put("${title}", "2026年度财务报表分析");
            textMap.put("${author}", "张三");
            // 2. 准备图片数据 (占位符建议包含特定前缀)
            Map<String, String> imageMap = new HashMap<>();
            imageMap.put("${imgChart1}", BASE_PATH + "/1.png");
            // 3. 准备表格数据 (占位符建议包含特定前缀)
            Map<String, String[][]> tableMap = new HashMap<>();
            String[][] tableData = {{"项目", "预算", "实际花费"}, {"研发部", "¥500,000", "¥480,000"}, {"市场部", "¥300,000", "¥310,000"}};
            tableMap.put("${tableData}", tableData);
            // 1. 自由组合：先改文字
            PptxTextProcessor.process(ppt, textMap);
            // 2. 自由组合：再换图片
            PptxImageProcessor.process(ppt, imageMap, "${img");
            // 3. 自由组合：生成表格
            PptxTableProcessor.process(ppt, tableMap, "${table");
            // 4. 自由组合：桑基图高级数据可视化
            Map<String, PptxSankeyProcessor.SankeyData> sankeyMap = new HashMap<>();
            // 构造 nodes
            List<Map<String, String>> nodes = new ArrayList<>();
            nodes.add(createMap("name", "首页"));
            nodes.add(createMap("name", "商品详情页"));
            nodes.add(createMap("name", "购物车"));
            nodes.add(createMap("name", "购买成功"));
            // 构造 links
            List<Map<String, Object>> links = new ArrayList<>();
            links.add(createLinkMap("source", "首页", "target", "商品详情页", "value", 1000));
            links.add(createLinkMap("source", "商品详情页", "target", "购物车", "value", 300));
            links.add(createLinkMap("source", "购物车", "target", "购买成功", "value", 100));
            sankeyMap.put("${sankeyUserFlow}", new PptxSankeyProcessor.SankeyData(nodes, links));
            // 执行桑基图替换
            PptxSankeyProcessor.process(ppt, sankeyMap, "${sankey");
            //5. 自由组合：正态分布
            Map<String, PptxNormalDistProcessor.NormalDistConfig> normDistMap = new HashMap<>();
            // 假设我们要展示工厂零件尺寸误差分布：均值 μ=10.0mm，标准差 σ=0.5mm
            normDistMap.put("${chart_normalCurve}", new PptxNormalDistProcessor.NormalDistConfig("零件尺寸误差分布", 10.0, 0.5));
            // 执行替换（它会在原占位符位置直接画一个原生的 PPT 散点/折线图）
            PptxNormalDistProcessor.process(ppt, normDistMap, "chart_");
            // 统一输出保存
            try (FileOutputStream fos = new FileOutputStream(OUTPUT_PATH)) {
                ppt.write(fos);
            }
        }
    }

}