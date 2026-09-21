package com.myproject.wmsserver.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审计日志查询接口的验收测试。
 *
 * 重点验两件事：
 *   1. 能按操作人 / 模块 / traceId 检索（不然这张表只能靠 DBA 查库）
 *   2. 只有 ADMIN 能查 —— 审计表记着所有模块的写操作和参数，是最敏感的一张表
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditLogQueryTest {

    private static final String PROBE_USER = "audit_viewer";
    private static final String PASSWORD = "test123456";
    private static final String TRACE = "trace-audit-probe-0001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String operatorToken;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM audit_log WHERE trace_id = ?", TRACE);
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", PROBE_USER);
        jdbcTemplate.update("INSERT INTO sys_user (username, password, real_name, role, status) VALUES (?, ?, '审计查看测试员', 'OPERATOR', 1)",
                PROBE_USER, passwordEncoder.encode(PASSWORD));

        // 造一条审计记录，用它验证过滤条件真的生效
        jdbcTemplate.update("INSERT INTO audit_log (trace_id, user_name, module, action, target_type, target_id, detail, ip, result, cost_ms) "
                + "VALUES (?, 'probe_operator', 'inventory', 'POST freeze', 'Inventory', '999', '{\"qty\":1}', '127.0.0.1', 0, 12)",
                TRACE);

        adminToken = login("admin", "123456");
        operatorToken = login(PROBE_USER, PASSWORD);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM audit_log WHERE trace_id = ?", TRACE);
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", PROBE_USER);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        Object token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
        return String.valueOf(token);
    }

    private String query(String token, String params) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/system/audit-logs" + params)
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    @DisplayName("★ 管理员能按 traceId / 操作人 / 模块检索审计日志（含失败的请求）")
    void adminCanQueryAuditLog() throws Exception {
        String byTrace = query(adminToken, "?page=1&size=10&traceId=" + TRACE);
        assertTrue(byTrace.contains(TRACE), "按 traceId 应该查得到：" + byTrace);
        assertTrue(byTrace.contains("POST freeze"), "动作要带出来");
        assertTrue(byTrace.contains("\"result\":0"), "失败的操作也要能查到");

        String byKeyword = query(adminToken, "?page=1&size=10&keyword=probe_operator");
        assertTrue(byKeyword.contains(TRACE), "按操作人模糊搜应该查得到");

        String byModule = query(adminToken, "?page=1&size=10&module=inventory&result=0");
        assertTrue(byModule.contains(TRACE), "模块 + 结果过滤应该查得到");

        String noMatch = query(adminToken, "?page=1&size=10&module=not-exist");
        assertTrue(noMatch.contains("\"total\":0"), "不匹配的条件应该返回空：" + noMatch);
    }

    @Test
    @DisplayName("作业员查不了审计日志（审计表是最敏感的一张）")
    void operatorCannotQueryAuditLog() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/system/audit-logs?page=1&size=10")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden())
                .andReturn();
        assertEquals(20003, (int) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.code"));
    }
}