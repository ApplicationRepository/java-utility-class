package com.uc.ppt;

import cn.hutool.core.io.FileUtil;
import com.uc.pptx.processor.*;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.Before;
import org.junit.Test;

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
        LOGGER.info("正在删除：{}。", OUTPUT_PATH);
        FileUtil.del(OUTPUT_PATH);
        LOGGER.info("删除{}完成。", OUTPUT_PATH);
    }

    @Test
    public void createPptxTest() throws Exception {
        try (FileInputStream fis = new FileInputStream(TEMPLATE_PATH); XMLSlideShow ppt = new XMLSlideShow(fis)) {
            //文本数据
            Map<String, String> textMap = new HashMap<>();
            textMap.put("${title}", "2026年度财务报表分析");
            textMap.put("${author}", "张三");

            //图片数据 (占位符建议包含特定前缀)
            Map<String, String> imageMap = new HashMap<>();
            imageMap.put("${imgChart1}", BASE_PATH + "/1.png");

            //表格数据 (占位符建议包含特定前缀)
            String[][] tableData = {
                    {"项目", "预算", "实际花费"},
                    {"研发部", "¥500,000", "¥480,000"},
                    {"市场部", "¥300,000", "¥310,000"}
            };
            Map<String, String[][]> tableMap = new HashMap<>();
            tableMap.put("${tableData}", tableData);

            //处理文本数据
            PptxTextProcessor.process(ppt, textMap);

            //处理图片
            PptxImageProcessor.process(ppt, imageMap, "${img");

            //处理表格
            PptxTableProcessor.process(ppt, tableMap, "${table");

            //处理桑基图
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

            //正态分布
            Map<String, PptxNormalDistProcessor.NormalDistConfig> normDistMap = new HashMap<>();
            // 假设我们要展示工厂零件尺寸误差分布：均值 μ=10.0mm，标准差 σ=0.5mm
            normDistMap.put("${chart_normalCurve}", new PptxNormalDistProcessor.NormalDistConfig("零件尺寸误差分布", 10.0, 0.5));
            // 执行替换（它会在原占位符位置直接画一个原生的 PPT 散点/折线图）
            PptxNormalDistProcessor.process(ppt, normDistMap, "chart_");

            //清理没有替换的文本框和占位符
            PptxClearProcessor.process(ppt);
            // 统一输出保存
            try (FileOutputStream fos = new FileOutputStream(OUTPUT_PATH)) {
                ppt.write(fos);
            }
        }
    }

}