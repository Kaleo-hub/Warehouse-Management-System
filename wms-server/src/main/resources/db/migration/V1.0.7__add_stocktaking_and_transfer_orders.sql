-- =============================================================================
-- 盘点单 / 移库单
-- -----------------------------------------------------------------------------
-- 原「库存作业」接口是即时动作：一次请求直接改一条库存。
-- 本迁移增加单据层，用来记录作业范围、逐行录入、批量过账和操作人。
-- 库存数量仍然只通过 InventoryService.applyChange 修改，单据不会绕开库存内核。
-- =============================================================================

CREATE TABLE `stocktaking_order`
(
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`      varchar(32)     NOT NULL COMMENT '盘点单号：ST + yyyyMMdd + 6位序号',
    `warehouse_id`  bigint unsigned NOT NULL COMMENT '仓库ID',
    `location_id`   bigint unsigned NOT NULL COMMENT '盘点库位ID',
    `location_code` varchar(32)     NOT NULL DEFAULT '' COMMENT '库位编码快照',
    `location_name` varchar(64)     NOT NULL DEFAULT '' COMMENT '库位名称快照',
    `status`        varchar(20)     NOT NULL DEFAULT 'COUNTING' COMMENT '状态：COUNTING/COMPLETED/CANCELLED',
    `total_items`   int             NOT NULL DEFAULT 0 COMMENT '盘点明细总数',
    `counted_items` int             NOT NULL DEFAULT 0 COMMENT '已录入实盘数量的明细数',
    `diff_items`    int             NOT NULL DEFAULT 0 COMMENT '有差异的明细数',
    `gain_qty`      int             NOT NULL DEFAULT 0 COMMENT '盘盈总数量',
    `loss_qty`      int             NOT NULL DEFAULT 0 COMMENT '盘亏总数量（正数）',
    `remark`        varchar(255)    NOT NULL DEFAULT '' COMMENT '备注',
    `post_time`     datetime(3)     NULL COMMENT '过账时间',
    `deleted`       tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time`   datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`   datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `create_by`     bigint          NULL COMMENT '创建人ID',
    `update_by`     bigint          NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stocktaking_order_no` (`order_no`),
    KEY `idx_stocktaking_warehouse_status` (`warehouse_id`, `status`, `create_time`),
    KEY `idx_stocktaking_location` (`location_id`, `status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '盘点单';

CREATE TABLE `stocktaking_order_item`
(
    `id`           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`     bigint unsigned NOT NULL COMMENT '盘点单ID',
    `inventory_id` bigint unsigned NOT NULL COMMENT '库存行ID',
    `sku_id`       bigint unsigned NOT NULL COMMENT '商品ID',
    `sku_code`     varchar(32)     NOT NULL DEFAULT '' COMMENT 'SKU编码快照',
    `sku_name`     varchar(128)    NOT NULL DEFAULT '' COMMENT '商品名称快照',
    `batch_no`     varchar(64)     NOT NULL DEFAULT '' COMMENT '批次号快照',
    `system_qty`   int             NOT NULL DEFAULT 0 COMMENT '建单时系统在库数量',
    `locked_qty`   int             NOT NULL DEFAULT 0 COMMENT '建单时锁定数量',
    `frozen_qty`   int             NOT NULL DEFAULT 0 COMMENT '建单时冻结数量',
    `actual_qty`   int             NULL COMMENT '实盘数量；NULL 表示尚未盘点',
    `diff_qty`     int             NULL COMMENT '差异：actual_qty - system_qty',
    `remark`       varchar(255)    NOT NULL DEFAULT '' COMMENT '备注',
    `deleted`      tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time`  datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`  datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `create_by`    bigint          NULL COMMENT '创建人ID',
    `update_by`    bigint          NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stocktaking_item_inventory` (`order_id`, `inventory_id`),
    KEY `idx_stocktaking_item_order` (`order_id`),
    CONSTRAINT `chk_stocktaking_item_qty`
        CHECK (`system_qty` >= 0 AND `locked_qty` >= 0 AND `frozen_qty` >= 0
            AND (`actual_qty` IS NULL OR `actual_qty` >= 0))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '盘点单明细';

CREATE TABLE `transfer_order`
(
    `id`               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`         varchar(32)     NOT NULL COMMENT '移库单号：TR + yyyyMMdd + 6位序号',
    `warehouse_id`     bigint unsigned NOT NULL COMMENT '仓库ID',
    `from_location_id` bigint unsigned NOT NULL COMMENT '移出库位ID',
    `from_location_code` varchar(32)   NOT NULL DEFAULT '' COMMENT '移出库位编码快照',
    `to_location_id`   bigint unsigned NOT NULL COMMENT '移入库位ID',
    `to_location_code` varchar(32)     NOT NULL DEFAULT '' COMMENT '移入库位编码快照',
    `status`           varchar(20)     NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED/COMPLETED/CANCELLED',
    `total_qty`        int             NOT NULL DEFAULT 0 COMMENT '移库总数量',
    `remark`           varchar(255)    NOT NULL DEFAULT '' COMMENT '备注',
    `transfer_time`    datetime(3)     NULL COMMENT '执行时间',
    `deleted`          tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time`      datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`      datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `create_by`        bigint          NULL COMMENT '创建人ID',
    `update_by`        bigint          NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_transfer_order_no` (`order_no`),
    KEY `idx_transfer_warehouse_status` (`warehouse_id`, `status`, `create_time`),
    KEY `idx_transfer_locations` (`from_location_id`, `to_location_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '移库单';

CREATE TABLE `transfer_order_item`
(
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`    bigint unsigned NOT NULL COMMENT '移库单ID',
    `sku_id`      bigint unsigned NOT NULL COMMENT '商品ID',
    `sku_code`    varchar(32)     NOT NULL DEFAULT '' COMMENT 'SKU编码快照',
    `sku_name`    varchar(128)    NOT NULL DEFAULT '' COMMENT '商品名称快照',
    `batch_no`    varchar(64)     NOT NULL DEFAULT '' COMMENT '批次号快照',
    `qty`         int             NOT NULL COMMENT '移库数量',
    `remark`      varchar(255)    NOT NULL DEFAULT '' COMMENT '备注',
    `deleted`     tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time` datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `create_by`   bigint          NULL COMMENT '创建人ID',
    `update_by`   bigint          NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_transfer_item_sku_batch` (`order_id`, `sku_id`, `batch_no`),
    KEY `idx_transfer_item_order` (`order_id`),
    CONSTRAINT `chk_transfer_item_qty` CHECK (`qty` > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '移库单明细';