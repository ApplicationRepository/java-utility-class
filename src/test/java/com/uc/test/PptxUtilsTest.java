package com.uc.test;

import cn.hutool.core.io.FileUtil;
import com.uc.pptx.PptxClearUtils;
import com.uc.pptx.PptxImageUtils;
import com.uc.pptx.PptxTableUtils;
import com.uc.pptx.PptxTextUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


@Log4j2
public class PptxUtilsTest {

    private static final String BASE_PATH = "/Users/administrator/Desktop/work-space/IDEA/java-utility-class/src/test/resources";
    private static final String TEMPLATE_PATH = BASE_PATH + "/pptx/template.pptx";
    private static final String OUTPUT_PATH = BASE_PATH + "/pptx/output.pptx";


    @Before
    public void beforeInitTest() {
        LOGGER.info("正在删除：{}。", OUTPUT_PATH);
        FileUtil.del(OUTPUT_PATH);
        LOGGER.info("删除{}完成。", OUTPUT_PATH);
    }

    @Test
    public void createPptxTest() {
        try (FileInputStream fis = new FileInputStream(TEMPLATE_PATH); XMLSlideShow ppt = new XMLSlideShow(fis)) {
            //文本数据
            Map<String, String> textMap = new HashMap<>();
            textMap.put("${title}", "2026年度财务报表分析");
            textMap.put("${author}", "张三");

            //图片数据 (占位符建议包含特定前缀)
            Map<String, String> imageMap = new HashMap<>();
            imageMap.put("${imgChart1}", BASE_PATH + "/image/1.png");

            //表格数据 (占位符建议包含特定前缀)
            String[][] tableData = {
                    {"项目", "预算", "实际花费"},
                    {"研发部", "¥500,000", "¥480,000"},
                    {"市场部", "¥300,000", "¥310,000"}
            };
            Map<String, String[][]> tableMap = new HashMap<>();
            tableMap.put("${tableData}", tableData);

            //处理文本数据
            PptxTextUtils.process(ppt, textMap);

            //处理图片
            PptxImageUtils.process(ppt, imageMap, "${img");

            //处理表格
            PptxTableUtils.process(ppt, tableMap, "${table");


            //清理没有替换的文本框和占位符
            PptxClearUtils.process(ppt);
            // 统一输出保存
            try (FileOutputStream fos = new FileOutputStream(OUTPUT_PATH)) {
                ppt.write(fos);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}