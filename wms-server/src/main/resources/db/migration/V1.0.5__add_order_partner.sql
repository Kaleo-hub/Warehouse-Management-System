-- =============================================================================
-- 单据接入往来单位主数据
-- -----------------------------------------------------------------------------
-- 上一步建好了 partner（供应商/客户）主数据，这一步让单据真正引用它。
--
-- 【只加 id，不动 supplier_name / customer_name】
-- 名称字段继续保留，作为【快照】：id 用来关联统计，名称快照用来保证
-- 「改了名字之后，历史单据还是当时那个名字」。
-- 只存 id 的话，改一次主数据，一年前的单据跟着变 —— 财务最忌讳这个。
--
-- 【为什么允许 NULL】
-- 历史单据（和之前灌进去的演示数据）没有 id，加 NOT NULL 就得给它们编一个，
-- 那是伪造数据。先允许为空，新单据由前端强制选择；等数据补齐了再收紧。
-- =============================================================================

ALTER TABLE `inbound_order`
    ADD COLUMN `supplier_id` bigint unsigned NULL COMMENT '供应商ID（partner.id，类型 SUPPLIER）' AFTER `warehouse_id`,
    ADD KEY `idx_supplier` (`supplier_id`);

ALTER TABLE `outbound_order`
    ADD COLUMN `customer_id` bigint unsigned NULL COMMENT '客户ID（partner.id，类型 CUSTOMER）' AFTER `warehouse_id`,
    ADD KEY `idx_customer` (`customer_id`);
