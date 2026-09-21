package com.myproject.wmsserver.system;

import com.myproject.wmsserver.common.exception.AuthErrorCode;
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
 * 角色权限的端到端测试。
 *
 * 验的是「认证 + 授权」这两件事是分开的：
 *   认证（你是谁）：JwtInterceptor 解 token，没 token 一律 401
 *   授权（你能干什么）：@PreAuthorize 看角色，角色不够 403 + 20003
 *
 * 用 MockMvc 打真实接口 —— 权限是在切面/拦截器里判的，直接调 Service 测不到。
 */
@SpringBootTest
@AutoConfigureMockMvc
class RolePermissionTest {

    private static final String PASSWORD = "test123456";
    private static final String WH_CODE = "TEST-PERM-WH";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String operatorToken;
    private String managerToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanup();
        createUser("perm_op", "权限测试作业员", "OPERATOR");
        createUser("perm_mgr", "权限测试主管", "MANAGER");

        operatorToken = login("perm_op");
        managerToken = login("perm_mgr");
        adminToken = login("admin");
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM warehouse WHERE code = ?", WH_CODE);
        jdbcTemplate.update("DELETE FROM sys_user WHERE username IN ('perm_op', 'perm_mgr')");
        jdbcTemplate.update("DELETE FROM audit_log WHERE user_name IN ('perm_op', 'perm_mgr')");
    }

    private void createUser(String username, String realName, String role) {
        jdbcTemplate.update("INSERT INTO sys_user (username, password, real_name, role, status) "
                        + "VALUES (?, ?, ?, ?, 1)",
                username, passwordEncoder.encode(PASSWORD), realName, role);
    }

    private String login(String username) throws Exception {
        String body = username.equals("admin")
                ? "{\"username\":\"admin\",\"password\":\"123456\"}"
                : "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        Object token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
        return String.valueOf(token);
    }

    /** 返回 [HTTP 状态码, 业务 code] */
    private int[] call(String method, String path, String token, String body) throws Exception {
        var request = method.equals("POST")
                ? post(path).contentType(MediaType.APPLICATION_JSON)
                : get(path);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.content(body);
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        String responseBody = result.getResponse().getContentAsString();
        Integer code = responseBody == null || responseBody.isBlank()
                ? null
                : (Integer) com.jayway.jsonpath.JsonPath.read(responseBody, "$.code");
        return new int[]{result.getResponse().getStatus(), code == null ? -1 : code};
    }

    /* ---------------------------- 用例 ---------------------------- */

    @Test
    @DisplayName("没有 token：任何接口都是 401")
    void anonymousShouldBeUnauthorized() throws Exception {
        int[] res = call("GET", "/api/v1/inventory/inventories?page=1&size=1", null, null);
        assertEquals(401, res[0]);
        assertEquals(AuthErrorCode.UNAUTHORIZED.getCode(), res[1]);
    }

    @Test
    @DisplayName("作业员：查询随便用，主数据 / 库存调整 / 用户管理都碰不了")
    void operatorPermissions() throws Exception {
        // 能查
        int[] query = call("GET", "/api/v1/inventory/inventories?page=1&size=1", operatorToken, null);
        assertEquals(200, query[0]);
        assertEquals(0, query[1], "查询接口所有登录用户都能用");

        // 库存调整（冻结）—— 403 + 20003
        int[] freeze = call("POST", "/api/v1/inventory/inventories/1/freeze", operatorToken, "{\"qty\":1}");
        assertEquals(403, freeze[0]);
        assertEquals(AuthErrorCode.FORBIDDEN.getCode(), freeze[1]);

        // 主数据维护 —— 403
        int[] warehouse = call("POST", "/api/v1/basedata/warehouses", operatorToken,
                "{\"code\":\"" + WH_CODE + "\",\"name\":\"权限测试仓\",\"status\":\"ENABLED\"}");
        assertEquals(403, warehouse[0]);

        // 用户管理 —— 403
        int[] users = call("GET", "/api/v1/system/users?page=1&size=1", operatorToken, null);
        assertEquals(403, users[0]);
        assertEquals(AuthErrorCode.FORBIDDEN.getCode(), users[1]);
    }

    @Test
    @DisplayName("主管：能维护主数据、能做库存调整，但管不了用户")
    void managerPermissions() throws Exception {
        int[] warehouse = call("POST", "/api/v1/basedata/warehouses", managerToken,
                "{\"code\":\"" + WH_CODE + "\",\"name\":\"权限测试仓\",\"status\":\"ENABLED\"}");
        assertEquals(200, warehouse[0]);
        assertEquals(0, warehouse[1], "主管可以维护主数据");

        Integer created = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM warehouse WHERE code = ?", Integer.class, WH_CODE);
        assertEquals(1, created, "仓库确实建出来了");

        int[] users = call("GET", "/api/v1/system/users?page=1&size=1", managerToken, null);
        assertEquals(403, users[0], "用户管理只有管理员能进");
    }

    @Test
    @DisplayName("管理员：能进用户管理")
    void adminPermissions() throws Exception {
        int[] users = call("GET", "/api/v1/system/users?page=1&size=1", adminToken, null);
        assertEquals(200, users[0]);
        assertEquals(0, users[1]);
    }

    @Test
    @DisplayName("被拒的请求也要进审计日志（result=0）")
    void forbiddenRequestShouldBeAudited() throws Exception {
        call("POST", "/api/v1/inventory/inventories/1/freeze", operatorToken, "{\"qty\":1}");

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE user_name = 'perm_op' AND result = 0", Integer.class);
        assertTrue(rows != null && rows > 0,
                "越权尝试本身就是要审计的行为，不能因为被拦下来就不记");
    }
}