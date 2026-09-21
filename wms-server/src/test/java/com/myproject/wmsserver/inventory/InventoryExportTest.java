package com.myproject.wmsserver.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Excel 导出的验收测试。
 *
 * 【怎么验一个二进制文件】不需要把它解析开 —— xlsx 本质是个 zip，
 * 只要开头两字节是 PK、内容长度合理、Content-Type 对，就说明
 * 「EasyExcel 真的写出文件了」而不是返回了一段错误 JSON。
 * （最容易出的 bug 就是：接口 200，但 body 是一个 {"code":10001}，
 *   前端存下来得到一个打不开的 .xlsx）
 */
@SpringBootTest
@AutoConfigureMockMvc
class InventoryExportTest {

    @Autowired
    private MockMvc mockMvc;

    private String token;

    @BeforeEach
    void login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn();
        Object t = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
        token = String.valueOf(t);
    }

    private void assertIsXlsx(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertNotNull(bytes);
        assertTrue(bytes.length > 1000, "导出的文件太小，多半不是真的 Excel：" + bytes.length);
        assertTrue(bytes[0] == 'P' && bytes[1] == 'K', "xlsx 是个 zip，开头必须是 PK");
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertNotNull(disposition, "要有下载头");
        assertTrue(disposition.contains("attachment"), "必须是下载而不是内联：" + disposition);
        assertTrue(disposition.contains("UTF-8''"), "中文文件名要按 RFC 5987 编码：" + disposition);
    }

    @Test
    @DisplayName("★ 导出库存：返回真正能打开的 xlsx（PK 开头 + 下载头 + 中文文件名）")
    void exportInventoryShouldReturnXlsx() throws Exception {
        assertIsXlsx("/api/v1/inventory/inventories/export?page=1&size=10");
    }

    @Test
    @DisplayName("★ 导出流水：同样返回 xlsx，且筛选条件跟列表页一致")
    void exportTransactionsShouldReturnXlsx() throws Exception {
        assertIsXlsx("/api/v1/inventory/transactions/export?page=1&size=10&txType=PUTAWAY_IN");
    }
}