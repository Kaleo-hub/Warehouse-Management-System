package com.myproject.wmsserver.inventory.service;

import com.myproject.wmsserver.inventory.dto.InventoryChangeCommand;

/**
 * 库存服务。
 *
 * 这个接口只有【一个】写方法，这是刻意的：
 * 全项目所有会改 inventory 表的业务，都必须走 applyChange。
 * 谁都不许在自己的 Service 里直接 UPDATE inventory —— 那样 BR-008（必写流水）就守不住了。
 */
public interface InventoryService {

    /**
     * 库存变动唯一入口：改库存 + 写流水，在同一个事务里完成。
     *
     * 调用方（上架、出库分配、移库、盘点）只需要描述「发生了什么」，
     * 不用关心锁、版本号、流水怎么写。
     *
     * 注意：这个方法必须由【别的 Bean】调用。
     * 同一个类里 A 方法调 B 方法不走 Spring 代理，@Transactional 会失效
     * （这是 docs/02 里提到的「自调用陷阱」）。
     *
     * @throws com.myproject.wmsserver.common.exception.BizException
     *         库存不足 / 可用库存不足 / 并发冲突
     */
    void applyChange(InventoryChangeCommand command);
}