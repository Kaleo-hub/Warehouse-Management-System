-- =============================================================================
-- 单号发号器
--
-- 为什么不直接用 MAX(order_no) + 1？
--   两个请求同时查到最大单号是 ...0005，就会同时生成 ...0006，
--   最后只有一个能插入成功（uk_order_no 挡住），另一个报错。
--   并发一高，用户就会看到"偶尔创建失败"。
--
-- 为什么不用 Redis INCR？
--   Redis 要到第 8 周才引入，而且发号这种小事不值得多一个故障点。
--
-- 这个表的原理：
--   INSERT ... ON DUPLICATE KEY UPDATE current_value = current_value + 1
--   InnoDB 会对这一行加排他锁，并发请求自动排队，天然不会重号。
-- =============================================================================

CREATE TABLE `sys_sequence`
(
    `biz_type`      varchar(32) NOT NULL COMMENT '业务类型：INBOUND_ORDER / OUTBOUND_ORDER / STOCK_TASK',
    `biz_date`      char(8)     NOT NULL COMMENT '业务日期 yyyyMMdd，每天从 1 重新开始',
    `current_value` bigint      NOT NULL DEFAULT 0 COMMENT '当前已发到的序号',
    `update_time`   datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`biz_type`, `biz_date`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '单号发号器';