-- =============================================================================
-- WMS 智能仓储管理系统 —— 初始化表结构（Flyway 迁移脚本）
-- -----------------------------------------------------------------------------
-- 版本    : V1.0.0
-- 数据库  : MySQL 8.0.16+（用到 CHECK 约束与生成列）
-- 字符集  : utf8mb4 / utf8mb4_0900_ai_ci
--
-- 【使用说明】
--   放到 src/main/resources/db/migration/ 下，Flyway 随应用启动自动执行。
--   为适配 Flyway，相比普通建表脚本做了三处调整：
--     1. 去掉 DROP TABLE IF EXISTS —— 避免误删已有数据的表
--     2. 去掉 SET NAMES —— 连接字符集由 JDBC 控制
--     3. 去掉末尾的校验 SELECT —— 迁移脚本不应产生结果集
--
--   注意：Flyway 会校验 checksum。已执行过的版本不要再改内容，
--         需要变更表结构请新增 V1.0.1__xxx.sql
-- =============================================================================

-- =============================================================================
-- 一、系统
-- =============================================================================

CREATE TABLE `sys_user`
(
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    varchar(50)     NOT NULL COMMENT '登录名',
    `password`    varchar(100)    NOT NULL COMMENT '密码（BCrypt 哈希）',
    `real_name`   varchar(50)     NOT NULL DEFAULT '' COMMENT '真实姓名',
    `role`        varchar(20)     NOT NULL DEFAULT 'OPERATOR' COMMENT '角色：ADMIN/MANAGER/OPERATOR',
    `status`      tinyint         NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `deleted`     tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time` datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `create_by`   bigint          NULL COMMENT '创建人ID',
    `update_by`   bigint          NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统用户';

-- =============================================================================
-- 二、基础数据
-- =============================================================================

CREATE TABLE `warehouse`
(
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        varchar(32)     NOT NULL COMMENT '仓库编码，全局唯一',
    `name`        varchar(64)     NOT NULL COMMENT '仓库名称',
    `address`     varchar(255)    NOT NULL DEFAULT '' COMMENT '仓库地址',
    `status`      varchar(20)     NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    `remark`      varchar(255)    NOT NULL DEFAULT '' COMMENT '备注',
    `deleted`     tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time` datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `create_by`   bigint          NULL COMMENT '创建人ID',
    `update_by`   bigint          NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    -- 注意：唯一键未包含 deleted，删除后编码不可复用。
    -- 若要支持复用，可把 deleted 加进唯一索引，但会出现「同一编码删两次」的新冲突，需配套改成时间戳型删除标记。
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '仓库';

CREATE TABLE `zone`
(
    `id`           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `warehouse_id` bigint unsigned NOT NULL COMMENT '所属仓库ID',
    `code`         varchar(32)     NOT NULL COMMENT '库区编码，仓库内唯一',
    `name`         varchar(64)     NOT NULL COMMENT '库区名称',
    `zone_type`    varchar(20)     NOT NULL COMMENT '类型：STORAGE/PICKING/RECEIVING/SHIPPING',
    `sort_no`      int             NOT NULL DEFAULT 0 COMMENT '排序号',
    `status`       varchar(20)     NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    `remark`       varchar(255)    NOT NULL DEFAULT '' COMMENT '备注',
    `deleted`      tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time`  datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`  datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `create_by`    bigint          NULL COMMENT '创建人ID',
    `update_by`    bigint          NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wh_code` (`warehouse_id`, `code`),
    KEY `idx_warehouse` (`warehouse_id`),
    KEY `idx_type` (`zone_type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '库区';

CREATE TABLE `location`
(
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `warehouse_id`  bigint unsigned NOT NULL COMMENT '所属仓库ID',
    `zone_id`       bigint unsigned NOT NULL COMMENT '所属库区ID',
    `code`          varchar(64)     NOT NULL COMMENT '库位编码，仓库内唯一',
    `name`          varchar(64)     NOT NULL DEFAULT '' COMMENT '库位名称',
    `location_type` varchar(20)     NOT NULL DEFAULT 'RACK' COMMENT '类型：RACK/FLOOR/TEMP',
    `capacity`      int             NOT NULL DEFAULT 0 COMMENT '容量上限（件），0表示不限',
    `used_capacity` int             NOT NULL DEFAULT 0 COMMENT '已用容量（件），与上架同事务维护',
    `status`        varchar(20)     NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED/FROZEN',
    `remark`        varchar(255)    NOT NULL DEFAULT '' COMMENT '备注',
    `deleted`       tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time`   datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`   datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `create_by`     bigint          NULL COMMENT '创建人ID',
    `update_by`     bigint          NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wh_code` (`warehouse_id`, `code`),
    KEY `idx_warehouse` (`warehouse_id`),
    KEY `idx_zone` (`zone_id`),
    KEY `idx_status` (`status`),
    CONSTRAINT `chk_location_capacity` CHECK (`capacity` >= 0 AND `used_capacity` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '库位';

CREATE TABLE `product`
(
    `id`              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `sku_code`        varchar(64)     NOT NULL COMMENT '商品编码(SKU)，全局唯一',
    `name`            varchar(128)    NOT NULL COMMENT '商品名称',
    `spec`            varchar(64)     NOT NULL DEFAULT '' COMMENT '规格，如 500ml/瓶',
    `unit`            varchar(16)     NOT NULL DEFAULT '件' COMMENT '最小计量单位',
    `barcode`         varchar(64)     NULL COMMENT '条形码，可为空；用 NULL 保证多条空值不冲突',
    `length_mm`       int             NULL COMMENT '长(mm)',
    `width_mm`        int             NULL COMMENT '宽(mm)',
    `height_mm`       int             NULL COMMENT '高(mm)',
    `weight_g`        int             NULL COMMENT '重量(g)',
    `shelf_life_days` int             NOT NULL DEFAULT 0 COMMENT '保质期天数，0表示不管效期',
    `status`          varchar(20)     NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    `remark`          varchar(255)    NOT NULL DEFAULT '' COMMENT '备注',
    `deleted`         tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time`     datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`     datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `create_by`       bigint          NULL COMMENT '创建人ID',
    `update_by`       bigint          NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sku_code` (`sku_code`),
    UNIQUE KEY `uk_barcode` (`barcode`),
    KEY `idx_name` (`name`),
    KEY `idx_status` (`status`),
    CONSTRAINT `chk_product_size` CHECK (
        (`length_mm` IS NULL OR `length_mm` >= 0)
            AND (`width_mm` IS NULL OR `width_mm` >= 0)
            AND (`height_mm` IS NULL OR `height_mm` >= 0)
            AND (`weight_g` IS NULL OR `weight_g` >= 0)
            AND `shelf_life_days` >= 0
        )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品(SKU)';

-- =============================================================================
-- 三、库存（核心）
-- =============================================================================

CREATE TABLE `inventory`
(
    `id`              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `warehouse_id`    bigint unsigned NOT NULL COMMENT '仓库ID',
    `location_id`     bigint unsigned NOT NULL COMMENT '库位ID',
    `sku_id`          bigint unsigned NOT NULL COMMENT '商品ID',
    `batch_no`        varchar(64)     NOT NULL DEFAULT '' COMMENT '批次号，无批次用空串（NULL 会破坏唯一约束）',
    `qty`             int             NOT NULL DEFAULT 0 COMMENT '在库实物数量',
    `locked_qty`      int             NOT NULL DEFAULT 0 COMMENT '锁定数量（已分配未出库）',
    `frozen_qty`      int             NOT NULL DEFAULT 0 COMMENT '冻结数量（盘点/质检）',
    `available_qty`   int GENERATED ALWAYS AS (`qty` - `locked_qty` - `frozen_qty`) STORED COMMENT '可用数量（生成列）',
    `version`         int             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `production_date` date            NULL COMMENT '生产日期，用于 FIFO',
    `expiry_date`     date            NULL COMMENT '到期日期',
    `inbound_time`    datetime(3)     NULL COMMENT '首次入库时间，用于 FIFO，只写一次',
    `deleted`         tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time`     datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`     datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `create_by`       bigint          NULL COMMENT '创建人ID',
    `update_by`       bigint          NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_loc_sku_batch` (`location_id`, `sku_id`, `batch_no`),
    KEY `idx_sku_wh` (`sku_id`, `warehouse_id`),
    KEY `idx_fifo` (`sku_id`, `warehouse_id`, `inbound_time`),
    KEY `idx_location` (`location_id`),
    KEY `idx_available` (`sku_id`, `warehouse_id`, `available_qty`),
    CONSTRAINT `chk_inventory_qty` CHECK (
        `qty` >= 0 AND `locked_qty` >= 0 AND `frozen_qty` >= 0
            AND (`locked_qty` + `frozen_qty`) <= `qty`
        )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '库存余额';

CREATE TABLE `inventory_transaction`
(
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tx_no`         varchar(40)     NOT NULL COMMENT '流水号，全局唯一，幂等键',
    `warehouse_id`  bigint unsigned NOT NULL COMMENT '仓库ID',
    `location_id`   bigint unsigned NOT NULL COMMENT '库位ID',
    `sku_id`        bigint unsigned NOT NULL COMMENT '商品ID',
    `batch_no`      varchar(64)     NOT NULL DEFAULT '' COMMENT '批次号',
    `tx_type`       varchar(32)     NOT NULL COMMENT '变动类型',
    `qty_change`    int             NOT NULL DEFAULT 0 COMMENT '实物数量变动，可正可负',
    `locked_change` int             NOT NULL DEFAULT 0 COMMENT '锁定数量变动',
    `frozen_change` int             NOT NULL DEFAULT 0 COMMENT '冻结数量变动',
    `qty_before`    int             NOT NULL DEFAULT 0 COMMENT '变动前 qty',
    `qty_after`     int             NOT NULL DEFAULT 0 COMMENT '变动后 qty',
    `source_type`   varchar(32)     NOT NULL DEFAULT '' COMMENT '来源类型：INBOUND_ORDER/OUTBOUND_ORDER/TASK/MANUAL/STOCKTAKING',
    `source_id`     bigint unsigned NULL COMMENT '来源单据ID',
    `source_no`     varchar(40)     NULL COMMENT '来源单据号',
    `operator_id`   bigint          NULL COMMENT '操作人ID',
    `operator_name` varchar(50)     NOT NULL DEFAULT '' COMMENT '操作人姓名（快照）',
    `remark`        varchar(255)    NOT NULL DEFAULT '' COMMENT '备注',
    `create_time`   datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tx_no` (`tx_no`),
    KEY `idx_sku_time` (`sku_id`, `create_time`),
    KEY `idx_source` (`source_type`, `source_id`),
    KEY `idx_location_time` (`location_id`, `create_time`),
    KEY `idx_biz_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '库存流水（只增不改不删）';

-- =============================================================================
-- 四、入库
-- =============================================================================

CREATE TABLE `inbound_order`
(
    `id`                 bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`           varchar(32)     NOT NULL COMMENT '入库单号，如 IN20260911000001',
    `warehouse_id`       bigint unsigned NOT NULL COMMENT '入库仓库ID',
    `supplier_name`      varchar(128)    NOT NULL DEFAULT '' COMMENT '供应商名称',
    `expect_arrive_time` datetime(3)     NULL COMMENT '预计到货时间',
    `status`             varchar(20)     NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED/RECEIVING/RECEIVED/PUTAWAY_DONE/CLOSED/CANCELLED',
    `total_expect_qty`   int             NOT NULL DEFAULT 0 COMMENT '预计总数量',
    `total_received_qty` int             NOT NULL DEFAULT 0 COMMENT '实收总数量',
    `remark`             varchar(255)    NOT NULL DEFAULT '' COMMENT '备注',
    `deleted`            tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time`        datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`        datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `create_by`          bigint          NULL COMMENT '创建人ID',
    `update_by`          bigint          NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_warehouse_status` (`warehouse_id`, `status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '入库单';

CREATE TABLE `inbound_order_item`
(
    `id`              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`        bigint unsigned NOT NULL COMMENT '入库单ID',
    `sku_id`          bigint unsigned NOT NULL COMMENT '商品ID',
    `sku_code`        varchar(64)     NOT NULL COMMENT '商品编码快照',
    `sku_name`        varchar(128)    NOT NULL COMMENT '商品名称快照',
    `expect_qty`      int             NOT NULL DEFAULT 0 COMMENT '预计数量',
    `received_qty`    int             NOT NULL DEFAULT 0 COMMENT '实收数量',
    `putaway_qty`     int             NOT NULL DEFAULT 0 COMMENT '已上架数量',
    `batch_no`        varchar(64)     NOT NULL DEFAULT '' COMMENT '批次号',
    `production_date` date            NULL COMMENT '生产日期',
    `expiry_date`     date            NULL COMMENT '到期日期',
    `remark`          varchar(255)    NOT NULL DEFAULT '' COMMENT '备注',
    `deleted`         tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time`     datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`     datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `create_by`       bigint          NULL COMMENT '创建人ID',
    `update_by`       bigint          NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`),
    KEY `idx_sku` (`sku_id`),
    CONSTRAINT `chk_inbound_item_qty` CHECK (`expect_qty` >= 0 AND `received_qty` >= 0 AND `putaway_qty` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '入库单明细';

-- =============================================================================
-- 五、出库
-- =============================================================================

CREATE TABLE `outbound_order`
(
    `id`               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`         varchar(32)     NOT NULL COMMENT '出库单号，如 OUT20260911000001',
    `warehouse_id`     bigint unsigned NOT NULL COMMENT '出库仓库ID',
    `customer_name`    varchar(128)    NOT NULL DEFAULT '' COMMENT '客户名称',
    `receive_address`  varchar(255)    NOT NULL DEFAULT '' COMMENT '收货地址',
    `expect_ship_time` datetime(3)     NULL COMMENT '要求发货时间',
    `status`           varchar(20)     NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED/ALLOCATED/PICKING/PICKED/SHIPPED/CLOSED/CANCELLED',
    `total_qty`        int             NOT NULL DEFAULT 0 COMMENT '需求总数量',
    `allocated_qty`    int             NOT NULL DEFAULT 0 COMMENT '已分配总数量',
    `picked_qty`       int             NOT NULL DEFAULT 0 COMMENT '已拣货总数量',
    `allocate_time`    datetime(3)     NULL COMMENT '分配完成时间，用于超时释放',
    `remark`           varchar(255)    NOT NULL DEFAULT '' COMMENT '备注',
    `deleted`          tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time`      datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`      datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `create_by`        bigint          NULL COMMENT '创建人ID',
    `update_by`        bigint          NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_warehouse_status` (`warehouse_id`, `status`),
    KEY `idx_allocate_time` (`status`, `allocate_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '出库单';

CREATE TABLE `outbound_order_item`
(
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`      bigint unsigned NOT NULL COMMENT '出库单ID',
    `sku_id`        bigint unsigned NOT NULL COMMENT '商品ID',
    `sku_code`      varchar(64)     NOT NULL COMMENT '商品编码快照',
    `sku_name`      varchar(128)    NOT NULL COMMENT '商品名称快照',
    `qty`           int             NOT NULL DEFAULT 0 COMMENT '需求数量',
    `allocated_qty` int             NOT NULL DEFAULT 0 COMMENT '已分配数量',
    `picked_qty`    int             NOT NULL DEFAULT 0 COMMENT '已拣货数量',
    `remark`        varchar(255)    NOT NULL DEFAULT '' COMMENT '备注',
    `deleted`       tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time`   datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`   datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `create_by`     bigint          NULL COMMENT '创建人ID',
    `update_by`     bigint          NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`),
    KEY `idx_sku` (`sku_id`),
    CONSTRAINT `chk_outbound_item_qty` CHECK (`qty` >= 0 AND `allocated_qty` >= 0 AND `picked_qty` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '出库单明细';

CREATE TABLE `outbound_allocate`
(
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`      bigint unsigned NOT NULL COMMENT '出库单ID',
    `order_item_id` bigint unsigned NOT NULL COMMENT '出库单明细ID',
    `inventory_id`  bigint unsigned NOT NULL COMMENT '锁定的库存记录ID',
    `warehouse_id`  bigint unsigned NOT NULL COMMENT '仓库ID',
    `location_id`   bigint unsigned NOT NULL COMMENT '库位ID',
    `sku_id`        bigint unsigned NOT NULL COMMENT '商品ID',
    `batch_no`      varchar(64)     NOT NULL DEFAULT '' COMMENT '批次号',
    `allocated_qty` int             NOT NULL DEFAULT 0 COMMENT '分配数量',
    `picked_qty`    int             NOT NULL DEFAULT 0 COMMENT '已拣货数量',
    `status`        varchar(20)     NOT NULL DEFAULT 'LOCKED' COMMENT '状态：LOCKED/PICKED/RELEASED',
    `deleted`       tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time`   datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`   datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `create_by`     bigint          NULL COMMENT '创建人ID',
    `update_by`     bigint          NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`),
    KEY `idx_order_item` (`order_item_id`),
    KEY `idx_inventory` (`inventory_id`),
    KEY `idx_status` (`status`),
    CONSTRAINT `chk_allocate_qty` CHECK (`allocated_qty` >= 0 AND `picked_qty` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '出库库存分配明细';

-- =============================================================================
-- 六、作业任务
-- =============================================================================

CREATE TABLE `stock_task`
(
    `id`               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_no`          varchar(32)     NOT NULL COMMENT '任务号，如 TASK20260911000001',
    `task_type`        varchar(20)     NOT NULL COMMENT '类型：PUTAWAY/PICK/TRANSFER/COUNT',
    `warehouse_id`     bigint unsigned NOT NULL COMMENT '仓库ID',
    `source_type`      varchar(32)     NOT NULL DEFAULT '' COMMENT '来源类型：INBOUND_ORDER/OUTBOUND_ORDER/MANUAL',
    `source_id`        bigint unsigned NULL COMMENT '来源单据ID',
    `source_no`        varchar(40)     NULL COMMENT '来源单据号',
    `source_item_id`   bigint unsigned NULL COMMENT '来源单据明细ID',
    `status`           varchar(20)     NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED/ASSIGNED/PROCESSING/DONE/CANCELLED/TIMEOUT/EXCEPTION',
    `priority`         int             NOT NULL DEFAULT 0 COMMENT '优先级，越大越优先',
    `assignee_id`      bigint          NULL COMMENT '执行人ID',
    `assignee_name`    varchar(50)     NULL COMMENT '执行人姓名',
    `from_location_id` bigint unsigned NULL COMMENT '源库位ID（拣货/移库）',
    `to_location_id`   bigint unsigned NULL COMMENT '目标库位ID（上架/移库）',
    `sku_id`           bigint unsigned NOT NULL COMMENT '商品ID',
    `batch_no`         varchar(64)     NOT NULL DEFAULT '' COMMENT '批次号',
    `plan_qty`         int             NOT NULL DEFAULT 0 COMMENT '计划数量',
    `actual_qty`       int             NOT NULL DEFAULT 0 COMMENT '实际完成数量',
    `deadline`         datetime(3)     NULL COMMENT '超时时间点',
    `start_time`       datetime(3)     NULL COMMENT '开始作业时间',
    `finish_time`      datetime(3)     NULL COMMENT '完成时间',
    `retry_count`      int             NOT NULL DEFAULT 0 COMMENT '重试次数',
    `remark`           varchar(255)    NOT NULL DEFAULT '' COMMENT '备注',
    `deleted`          tinyint         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    `create_time`      datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`      datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `create_by`        bigint          NULL COMMENT '创建人ID',
    `update_by`        bigint          NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_no` (`task_no`),
    KEY `idx_status_priority` (`status`, `priority`, `create_time`),
    KEY `idx_source` (`source_type`, `source_id`),
    KEY `idx_assignee` (`assignee_id`, `status`),
    KEY `idx_deadline` (`status`, `deadline`),
    KEY `idx_warehouse_type` (`warehouse_id`, `task_type`, `status`),
    CONSTRAINT `chk_task_qty` CHECK (`plan_qty` >= 0 AND `actual_qty` >= 0 AND `retry_count` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '作业任务';

-- =============================================================================
-- 七、支撑表
-- =============================================================================

CREATE TABLE `idempotent_record`
(
    `id`              bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `idempotent_key`  varchar(128)    NOT NULL COMMENT '幂等键，如 OUTBOUND_ALLOC:OUT20260911000001',
    `biz_type`        varchar(32)     NOT NULL COMMENT '业务类型',
    `request_hash`    varchar(64)     NULL COMMENT '请求体哈希，用于检测同 key 不同参数',
    `status`          tinyint         NOT NULL DEFAULT 0 COMMENT '状态：0处理中 1成功 2失败',
    `result_snapshot` text            NULL COMMENT '成功结果快照，用于重放响应',
    `expire_time`     datetime(3)     NULL COMMENT '过期时间，定时清理',
    `create_time`     datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`     datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_idempotent_key` (`idempotent_key`),
    KEY `idx_expire_time` (`expire_time`),
    KEY `idx_biz_type` (`biz_type`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '幂等记录';

CREATE TABLE `audit_log`
(
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `trace_id`    varchar(64)     NOT NULL DEFAULT '' COMMENT '链路ID',
    `user_id`     bigint          NULL COMMENT '操作人ID',
    `user_name`   varchar(50)     NOT NULL DEFAULT '' COMMENT '操作人姓名',
    `module`      varchar(32)     NOT NULL DEFAULT '' COMMENT '模块',
    `action`      varchar(32)     NOT NULL DEFAULT '' COMMENT '动作',
    `target_type` varchar(32)     NOT NULL DEFAULT '' COMMENT '操作对象类型',
    `target_id`   varchar(64)     NULL COMMENT '操作对象ID',
    `detail`      text            NULL COMMENT '变更详情(JSON)',
    `ip`          varchar(64)     NOT NULL DEFAULT '' COMMENT '来源IP',
    `result`      tinyint         NOT NULL DEFAULT 1 COMMENT '结果：1成功 0失败',
    `error_msg`   varchar(255)    NULL COMMENT '失败原因',
    `cost_ms`     bigint          NULL COMMENT '耗时(毫秒)',
    `create_time` datetime(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_trace_id` (`trace_id`),
    KEY `idx_user_time` (`user_id`, `create_time`),
    KEY `idx_module` (`module`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '审计日志';

-- =============================================================================
-- 八、初始化数据
-- =============================================================================

-- 初始账号：admin / 123456
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `role`, `status`, `create_by`)
VALUES ('admin',
        '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
        '系统管理员',
        'ADMIN',
        1,
        1);

INSERT INTO `warehouse` (`code`, `name`, `address`, `status`, `remark`, `create_by`)
VALUES ('WH001', '上海一号仓', '上海市浦东新区示例路 100 号', 'ENABLED', '示例数据', 1);

INSERT INTO `zone` (`warehouse_id`, `code`, `name`, `zone_type`, `sort_no`, `status`, `create_by`)
VALUES (1, 'A', 'A区存储区', 'STORAGE', 1, 'ENABLED', 1),
       (1, 'B', 'B区拣货区', 'PICKING', 2, 'ENABLED', 1),
       (1, 'R', '收货暂存区', 'RECEIVING', 3, 'ENABLED', 1);

INSERT INTO `location` (`warehouse_id`, `zone_id`, `code`, `name`, `location_type`, `capacity`, `used_capacity`, `status`, `create_by`)
VALUES (1, 1, 'A-01-01-01', 'A区01排01列01层', 'RACK', 1000, 0, 'ENABLED', 1),
       (1, 1, 'A-01-01-02', 'A区01排01列02层', 'RACK', 1000, 0, 'ENABLED', 1),
       (1, 1, 'A-01-02-01', 'A区01排02列01层', 'RACK', 1000, 0, 'ENABLED', 1),
       (1, 3, 'R-00-00-01', '收货暂存位01', 'TEMP', 0, 0, 'ENABLED', 1);

INSERT INTO `product` (`sku_code`, `name`, `spec`, `unit`, `barcode`, `weight_g`, `shelf_life_days`, `status`, `create_by`)
VALUES ('SKU0001', '洗发水 500ml', '500ml/瓶', '瓶', '6900000000011', 520, 730, 'ENABLED', 1),
       ('SKU0002', '沐浴露 750ml', '750ml/瓶', '瓶', '6900000000028', 800, 730, 'ENABLED', 1),
       ('SKU0003', '纸巾 200抽', '200抽/包', '包', '6900000000035', 300, 1095, 'ENABLED', 1);