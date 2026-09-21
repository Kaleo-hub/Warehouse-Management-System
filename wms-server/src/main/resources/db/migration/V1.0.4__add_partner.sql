-- =============================================================================
-- 供应商 / 客户主数据
-- -----------------------------------------------------------------------------
-- 背景：入库单的供应商、出库单的客户原来是【自由文本】，
-- 于是同一家公司会出现「XX公司 / XX有限公司 / xx公司」三种写法，
-- 按供应商统计、对账、发消息全都做不了。
--
-- 设计取舍：
--   1. 供应商和客户合成一张 partner 表，用 partner_type 区分。
--      两者字段几乎一样（编码/名称/联系人/电话/地址/状态），
--      拆两张表的唯一好处是约束更严，代价是 CRUD 代码要写两遍。
--      真出现「供应商有账期、客户有信用额度」这种分化时再拆也不迟。
--   2. 单据上【同时保留 id 和名称快照】：id 用来关联统计，
--      名称快照用来保证「改了名字之后，历史单据还是当时那个名字」。
--      只存 id 的话，历史单据会跟着主数据一起变，那是财务最忌讳的事。
--
-- 注意：Flyway 会校验 checksum，已执行过的版本不能改，只能新增版本号。
-- =============================================================================

CREATE TABLE `partner`
(
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `partner_type`  varchar(20)     NOT NULL COMMENT '类型：SUPPLIER 供应商 / CUSTOMER 客户',
    `code`          varchar(32)     NOT NULL COMMENT '编码，全局唯一',
    `name`          varchar(128)    NOT NULL COMMENT '名称',
    `contact_name`  varchar(50)     NOT NULL DEFAULT '' COMMENT '联系人',
    `contact_phone` varchar(32)     NOT NULL DEFAULT '' COMMENT '联系电话',
    `address`       varchar(255)    NOT NULL DEFAULT '' COMMENT '地址',
    `status`        varchar(20)     NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    `remark`        varchar(255)    NOT NULL DEFAULT '' COMMENT '备注',
    `deleted`       tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time`   datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`   datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `create_by`     bigint          NULL COMMENT '创建人ID',
    `update_by`     bigint          NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    -- 编码全局唯一（不区分供应商/客户）：让「编码」成为跨类型也认得的身份，
    -- 以后要合并两类数据、或做统一对账时不会撞号
    UNIQUE KEY `uk_partner_code` (`code`),
    KEY `idx_type_status` (`partner_type`, `status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '往来单位（供应商/客户）';

-- 演示数据：和种子数据里入库单用过的供应商名保持一致，方便肉眼对照
INSERT INTO `partner` (`partner_type`, `code`, `name`, `contact_name`, `contact_phone`, `status`, `create_by`)
VALUES ('SUPPLIER', 'SUP001', '示例供应商', '张经理', '13800000001', 'ENABLED', 1),
       ('CUSTOMER', 'CUS001', '示例客户', '李经理', '13900000001', 'ENABLED', 1);