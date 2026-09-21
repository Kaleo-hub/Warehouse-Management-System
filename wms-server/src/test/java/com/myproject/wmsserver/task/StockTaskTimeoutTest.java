package com.myproject.wmsserver.task;

import com.myproject.wmsserver.task.job.StockTaskTimeoutJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务级超时的验收测试（docs/01 第 6.3 节状态机）。
 *
 * 【为什么不走完整的建单/收货/生成任务流程】
 * 这里要验的是「超时判定 + 重新入池」这一段，和上游怎么建任务无关。
 * 直接插一条 deadline 已过期的任务，把 job 跑一遍看状态怎么走 —— 更快也更准。
 * 用独立的 task_no，@AfterEach 物理清理，不碰真实数据。
 */
@SpringBootTest
class StockTaskTimeoutTest {

    private static final String TASK_NO = "T-TIMEOUT-TEST";

    @Autowired
    private StockTaskTimeoutJob timeoutJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM stock_task WHERE task_no = ?", TASK_NO);
    }

    /** 造一条「已领取但超时未完成」的拣货任务 */
    private void insertTimeoutTask() {
        jdbcTemplate.update("DELETE FROM stock_task WHERE task_no = ?", TASK_NO);
        jdbcTemplate.update(
                "INSERT INTO stock_task (task_no, task_type, warehouse_id, source_type, status, "
                        + "assignee_id, assignee_name, sku_id, batch_no, plan_qty, actual_qty, "
                        + "retry_count, deadline, deleted) "
                        + "VALUES (?, 'PICK', 1, 'MANUAL', 'ASSIGNED', 2, '测试作业员', 1, '', 10, 0, "
                        + "0, ?, 0)",
                TASK_NO, LocalDateTime.now().minusMinutes(5));
    }

    private Map<String, Object> task() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT status, retry_count, assignee_id, assignee_name, deadline "
                        + "FROM stock_task WHERE task_no = ?", TASK_NO);
        return rows.get(0);
    }

    @Test
    @DisplayName("★ 任务超时：先标 TIMEOUT，再重新入池一次（清掉领取人、顺延时限）")
    void timeoutTaskShouldBeRequeuedOnce() {
        insertTimeoutTask();

        timeoutJob.handleTimeoutTasks();

        Map<String, Object> after = task();
        assertEquals("CREATED", after.get("status"), "超时后应该重新入池");
        assertEquals(1, ((Number) after.get("retry_count")).intValue(), "重试次数要 +1");
        assertNull(after.get("assignee_id"), "已经不是那个人的活了，领取人要清掉");
        assertNull(after.get("assignee_name"));
        assertNotNull(after.get("deadline"));
        assertTrue(((LocalDateTime) after.get("deadline")).isAfter(LocalDateTime.now()),
                "回池时必须顺延新的时限，否则下一轮扫描立刻又超时");
    }

    @Test
    @DisplayName("★ 重试过之后不再回池：停在 TIMEOUT 等人处理（不能无限重试）")
    void retriedTaskShouldStayTimeout() {
        insertTimeoutTask();
        timeoutJob.handleTimeoutTasks();          // 第一次：回池，retry_count = 1
        assertEquals("CREATED", task().get("status"));

        // 再让它超时一次
        jdbcTemplate.update("UPDATE stock_task SET deadline = ? WHERE task_no = ?",
                LocalDateTime.now().minusMinutes(5), TASK_NO);
        timeoutJob.handleTimeoutTasks();

        Map<String, Object> after = task();
        assertEquals("TIMEOUT", after.get("status"), "重试过就不再放回池子");
        assertEquals(1, ((Number) after.get("retry_count")).intValue(), "重试次数不会再涨");
    }

    @Test
    @DisplayName("没超时的任务不被动：deadline 还没到就什么都不做")
    void freshTaskShouldNotBeTouched() {
        insertTimeoutTask();
        jdbcTemplate.update("UPDATE stock_task SET deadline = ? WHERE task_no = ?",
                LocalDateTime.now().plusMinutes(30), TASK_NO);

        timeoutJob.handleTimeoutTasks();

        Map<String, Object> after = task();
        assertEquals("ASSIGNED", after.get("status"), "还没到时限，不能被改");
        assertEquals(0, ((Number) after.get("retry_count")).intValue());
    }
}