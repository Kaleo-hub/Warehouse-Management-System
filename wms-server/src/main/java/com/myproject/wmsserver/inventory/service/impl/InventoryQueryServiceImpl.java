package com.myproject.wmsserver.inventory.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.inventory.dto.InventoryQuery;
import com.myproject.wmsserver.inventory.dto.TransactionQuery;
import com.myproject.wmsserver.inventory.mapper.InventoryMapper;
import com.myproject.wmsserver.inventory.mapper.InventoryTransactionMapper;
import com.myproject.wmsserver.inventory.service.InventoryQueryService;
import com.myproject.wmsserver.inventory.vo.InventoryTransactionVO;
import com.myproject.wmsserver.inventory.vo.InventoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 库存查询实现。只读，所以没有 @Transactional */
@Service
@RequiredArgsConstructor
public class InventoryQueryServiceImpl implements InventoryQueryService {

    private final InventoryMapper inventoryMapper;
    private final InventoryTransactionMapper transactionMapper;

    @Override
    public PageResult<InventoryVO> pageInventory(InventoryQuery query) {
        Page<InventoryVO> page = new Page<>(query.getPage(), query.getSize());
        inventoryMapper.selectInventoryPage(page, query);
        return PageResult.of(page, page.getRecords());
    }

    @Override
    public PageResult<InventoryTransactionVO> pageTransaction(TransactionQuery query) {
        Page<InventoryTransactionVO> page = new Page<>(query.getPage(), query.getSize());
        transactionMapper.selectTransactionPage(page, query);
        return PageResult.of(page, page.getRecords());
    }
}