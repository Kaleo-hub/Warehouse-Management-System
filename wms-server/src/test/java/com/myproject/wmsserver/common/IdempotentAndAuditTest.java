package com.myproject.wmsserver.common;

import com.myproject.wmsserver.common.exception.InboundErrorCode;
import com.myproject.wmsserver.common.idempotent.IdempotentCleanupJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 幂等 + 审计日志的端到端测试。
 *
 * 【为什么用 MockMvc 打真实接口，而不是直接调 Service】
 * 要验的正是「切面 + 拦截器 + 控制器」这一整条链路：
 *   登录拿 token（JwtInterceptor）→ 带 X-Idempotent-Key → 幂等切面 → 控制器 → 审计切面
 * 直接调 Service 的话，切面根本不会触发，测了等于没测。
 */
@SpringBootTest
@AutoConfigureMockMvc
class IdempotentAndAuditTest {

    private static final String SKU_CODE = "TEST-IDEM-SKU";
    private static final String SUPPLIER = "幂等测试供应商";
    private static final String AUDIT_USER = "audit_probe";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IdempotentCleanupJob idempotentCleanupJob;

    private Long testSkuId;
    private String token;
    private Long createdOrderId;

    @BeforeEach
    void setUp() throws Exception {
        cleanup();
        jdbcTemplate.update("INSERT INTO product (sku_code, name, unit, shelf_life_days, status) "
                + "VALUES (?, '幂等测试商品', '件', 365, 'ENABLED')", SKU_CODE);
        testSkuId = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE sku_code = ?", Long.class, SKU_CODE);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        token = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.data.token");
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM idempotent_record WHERE biz_type LIKE 'INBOUND_%'");
        jdbcTemplate.update("DELETE FROM audit_log WHERE user_name = 'admin' AND module IN ('inbound', 'system')");
        jdbcTemplate.update("DELETE FROM inbound_order_item WHERE order_id IN "
                + "(SELECT id FROM inbound_order WHERE supplier_name = ?)", SUPPLIER);
        jdbcTemplate.update("DELETE FROM inbound_order WHERE supplier_name = ?", SUPPLIER);
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", AUDIT_USER);
        jdbcTemplate.update("DELETE FROM product WHERE sku_code = ?", SKU_CODE);
        createdOrderId = null;
    }

    /* ---------------------------- 工具 ---------------------------- */

    private String createBody(int expectQty) {
        return "{\"warehouseId\":1,\"supplierName\":\"" + SUPPLIER + "\",\"items\":[{\"skuId\":"
                + testSkuId + ",\"expectQty\":" + expectQty + "}]}";
    }

    /** 用指定的幂等 key 建单，返回响应体 */
    private String createOrder(String idempotentKey, int expectQty) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/inbound/inbound-orders")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotent-Key", idempotentKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(expectQty)))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        Object id = com.jayway.jsonpath.JsonPath.read(body, "$.data");
        if (id != null) {
            createdOrderId = Long.valueOf(String.valueOf(id));
        }
        return body;
    }

    private int orderCount() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbound_order WHERE supplier_name = ?", Integer.class, SUPPLIER);
        return n == null ? 0 : n;
    }

    private Long firstItemId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM inbound_order_item WHERE order_id = ? ORDER BY id LIMIT 1",
                Long.class, createdOrderId);
    }

    private String receiveBody(int qty) {
        return "{\"items\":[{\"itemId\":" + firstItemId() + ",\"receivedQty\":" + qty
                + ",\"batchNo\":\"IDEM-B1\"}]}";
    }

    private MvcResult receive(String idempotentKey, int qty) throws Exception {
        return mockMvc.perform(post("/api/v1/inbound/inbound-orders/" + createdOrderId + "/receive")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotent-Key", idempotentKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiveBody(qty)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private int receivedQty() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT received_qty FROM inbound_order_item WHERE order_id = ? ORDER BY id LIMIT 1",
                Integer.class, createdOrderId);
        return n == null ? 0 : n;
    }

    /**
     * 把某条幂等记录改回「处理中」，并指定它的租约到期时间。
     *
     * 【为什么要这么模拟】现实里这条记录是「抢位成功但进程没回来销登记」留下的，
     * 而 key 里带着请求体哈希 —— 手工拼一个一模一样的 key 很麻烦。
     * 所以先让一次真实请求把它建出来（此时是 status=1），再把它改成
     * 「处理中 + 指定租约」，等价于那一次执行永远没完成。
     */
    private void makeProcessing(String bizType, int leaseOffsetSeconds) {
        jdbcTemplate.update(
                "UPDATE idempotent_record SET status = 0, locked_until = ? "
                        + "WHERE biz_type = ? AND id = (SELECT max_id FROM ("
                        + "SELECT MAX(id) AS max_id FROM idempotent_record WHERE biz_type = ?) t)",
                LocalDateTime.now().plusSeconds(leaseOffsetSeconds), bizType, bizType);
    }

    private LocalDateTime lockedUntil(String bizType) {
        return jdbcTemplate.queryForObject(
                "SELECT locked_until FROM idempotent_record WHERE biz_type = ? ORDER BY id DESC LIMIT 1",
                LocalDateTime.class, bizType);
    }

    private int idempotentStatus(String bizType) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT status FROM idempotent_record WHERE biz_type = ? ORDER BY id DESC LIMIT 1",
                Integer.class, bizType);
        return n == null ? -1 : n;
    }

    /* ---------------------------- 用例 ---------------------------- */

    @Test
    @DisplayName("★ 同一个幂等键连续提交三次：只建一张单，后两次拿到的是同一份响应")
    void duplicateCreateShouldOnlyCreateOneOrder() throws Exception {
        String key = UUID.randomUUID().toString();

        String first = createOrder(key, 10);
        String second = createOrder(key, 10);
        String third = createOrder(key, 10);

        assertEquals(1, orderCount(), "三次提交只能建一张单");
        // 响应也应该是同一份（重放上次的快照）。转成字符串比，避免 JsonPath 泛型推断带来的重载歧义
        // 注意：JsonPath.read 是泛型方法，直接套进 String.valueOf(...) 会被推断成 String.valueOf(char[])
        // 从而报 ClassCastException，所以先落到 Object 变量上再转字符串。
        Object firstData = com.jayway.jsonpath.JsonPath.read(first, "$.data");
        Object secondData = com.jayway.jsonpath.JsonPath.read(second, "$.data");
        Object thirdData = com.jayway.jsonpath.JsonPath.read(third, "$.data");
        assertEquals(String.valueOf(firstData), String.valueOf(secondData));
        assertEquals(String.valueOf(firstData), String.valueOf(thirdData));
        assertEquals(1, idempotentStatus("INBOUND_CREATE"), "记录状态应该是 1（成功）");
    }

    @Test
    @DisplayName("同一个键但参数不同：这是另一件事，必须放行（比如改数量后重提）")
    void sameKeyDifferentPayloadShouldCreateNewOrder() throws Exception {
        String key = UUID.randomUUID().toString();

        createOrder(key, 10);
        createOrder(key, 20);

        assertEquals(2, orderCount(), "请求体不同 → 幂等键里的哈希不同 → 是两次不同的请求");
    }

    @Test
    @DisplayName("★ 重复收货：同一批货提交两次，库存只收一次（这是最危险的一个接口）")
    void duplicateReceiveShouldOnlyApplyOnce() throws Exception {
        String createKey = UUID.randomUUID().toString();
        createOrder(createKey, 100);

        String receiveKey = UUID.randomUUID().toString();
        receive(receiveKey, 30).getResponse();
        receive(receiveKey, 30).getResponse();

        assertEquals(30, receivedQty(), "重复提交同一批收货，累计实收必须还是 30");

        // 换了数量就是另一批货（同一天分两次到货），必须能收进来
        receive(receiveKey, 20);
        assertEquals(50, receivedQty(), "内容不同的第二批收货要被放行");
    }

    @Test
    @DisplayName("处理中且租约【未】到期：拒绝重复提交（人家确实在跑）")
    void processingWithinLeaseShouldBeRejected() throws Exception {
        String key = UUID.randomUUID().toString();
        createOrder(key, 10);
        int before = orderCount();

        // 模拟「另一个请求正在处理，租约还有 30 秒」
        makeProcessing("INBOUND_CREATE", 30);

        MvcResult again = mockMvc.perform(post("/api/v1/inbound/inbound-orders")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotent-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(10)))
                .andReturn();
        String body = again.getResponse().getContentAsString();
        assertTrue(body.contains("10007"), "租约没过期就该拒绝：" + body);
        assertEquals(before, orderCount(), "被拒的请求不能真的建单");
    }

    @Test
    @DisplayName("★ 处理中但租约【已】过期：判定持锁进程已死，后来者接手执行")
    void expiredLeaseShouldAllowTakeover() throws Exception {
        String key = UUID.randomUUID().toString();
        createOrder(key, 10);
        int before = orderCount();

        // 模拟「抢位成功、进程随后崩了」：状态卡在处理中，租约早就过期
        makeProcessing("INBOUND_CREATE", -300);

        MvcResult takeover = mockMvc.perform(post("/api/v1/inbound/inbound-orders")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Idempotent-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(10)))
                .andReturn();
        String body = takeover.getResponse().getContentAsString();
        assertTrue(body.contains("\"code\":0"), "租约过期后应该能接手执行：" + body);
        assertEquals(before + 1, orderCount(), "接手方要真的把这次业务做掉");
        assertEquals(1, idempotentStatus("INBOUND_CREATE"), "接手完成后记录应该是成功");
        assertTrue(lockedUntil("INBOUND_CREATE") == null, "完成后租约要清掉，免得让人误会还在处理");

        // 说明：这里接手后确实执行了第二次（所以单数 +1）。
        // 现实中「卡住的记录」意味着第一次执行根本没完成，接手正是我们要的；
        // 而如果第一次只是【慢】，接手就会造成真的重复 ——
        // 这就是租约必须远大于业务耗时（30 秒 vs 毫秒级）的原因。
    }

    @Test
    @DisplayName("定时兜底：卡住很久的「处理中」会被标成失败，同 key 之后能正常重试")
    void staleProcessingShouldBeMarkedFailedByHousekeeping() throws Exception {
        String key = UUID.randomUUID().toString();
        createOrder(key, 10);

        // 租约过期 2 小时（远超兜底阈值 60 分钟）
        makeProcessing("INBOUND_CREATE", -7200);

        idempotentCleanupJob.housekeeping();

        assertEquals(2, idempotentStatus("INBOUND_CREATE"), "兜底任务应该把它标成失败");
        String errorMsg = jdbcTemplate.queryForObject(
                "SELECT error_msg FROM idempotent_record WHERE biz_type = ? ORDER BY id DESC LIMIT 1",
                String.class, "INBOUND_CREATE");
        assertTrue(errorMsg != null && errorMsg.contains("超时"), "要留下能看懂的原因：" + errorMsg);

        // 标成失败之后，同一个 key 再来就是「允许重试」，不再被 10007 挡死
        int before = orderCount();
        String retryBody = createOrder(key, 10);
        assertTrue(retryBody.contains("\"code\":0"), "应该放行重试：" + retryBody);
        assertEquals(before + 1, orderCount());
    }

    @Test
    @DisplayName("业务失败后允许重试：不能因为「失败过」就把用户永久挡在门外")
    void failedRequestShouldBeRetryable() throws Exception {
        createOrder(UUID.randomUUID().toString(), 10);

        String key = UUID.randomUUID().toString();
        // 预计 10 却收 999 —— 业务上必须失败
        MvcResult failed = receive(key, 999);
        String failedBody = failed.getResponse().getContentAsString();
        assertTrue(failedBody.contains(String.valueOf(InboundErrorCode.RECEIVED_EXCEED_EXPECT.getCode())),
                "第一次应该是业务失败：" + failedBody);
        assertEquals(2, idempotentStatus("INBOUND_RECEIVE"), "失败要落 status=2");

        // 同一个 key 再来一次：应该【继续执行业务】（再次得到业务错误），而不是被当成重复请求（10007）
        MvcResult retry = receive(key, 999);
        String retryBody = retry.getResponse().getContentAsString();
        assertTrue(retryBody.contains(String.valueOf(InboundErrorCode.RECEIVED_EXCEED_EXPECT.getCode())),
                "重试应该再次执行业务并得到同样的业务错误，而不是 10007：" + retryBody);
        assertTrue(!retryBody.contains("10007"), "失败过的请求不能被幂等挡住");
    }

    @Test
    @DisplayName("★ 审计日志：写接口自动留痕，密码字段脱敏")
    void auditLogShouldRecordWriteAndMaskPassword() throws Exception {
        String key = UUID.randomUUID().toString();
        createOrder(key, 10);

        Integer inboundRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE module = 'inbound' AND action = 'POST create' "
                        + "AND target_type = 'InboundOrder' AND result = 1", Integer.class);
        assertTrue(inboundRows != null && inboundRows > 0, "建单应该留下审计记录");

        // 建用户接口带密码，审计里绝不能出现明文
        mockMvc.perform(post("/api/v1/system/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + AUDIT_USER + "\",\"realName\":\"审计探针\","
                                + "\"role\":\"OPERATOR\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        List<String> details = jdbcTemplate.queryForList(
                "SELECT detail FROM audit_log WHERE module = 'system' AND action = 'POST create' "
                        + "ORDER BY id DESC LIMIT 1", String.class);
        assertEquals(1, details.size(), "应该有审计记录");
        String detail = details.get(0);
        assertTrue(detail.contains("***"), "密码字段要脱敏：" + detail);
        assertTrue(!detail.contains("secret123"), "审计里不能出现明文密码：" + detail);

        // 失败的请求也要留痕（result=0）
        createOrder(UUID.randomUUID().toString(), 10);
        receive(UUID.randomUUID().toString(), 999);
        Integer failedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE module = 'inbound' AND action = 'POST receive' "
                        + "AND result = 0", Integer.class);
        assertTrue(failedRows != null && failedRows > 0, "失败的写请求同样要进审计");
    }
}