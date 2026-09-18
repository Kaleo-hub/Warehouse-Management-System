package com.myproject.wmsserver.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.myproject.wmsserver.inventory.dto.TransactionQuery;
import com.myproject.wmsserver.inventory.entity.InventoryTransaction;
import com.myproject.wmsserver.inventory.vo.InventoryTransactionVO;
import org.apache.ibatis.annotations.Param;

/**
 * 流水只需要插入和查询，BaseMapper 够用。
 * 千万不要给这张表写 update / delete 的业务方法 —— BR-008 要求流水只增不改不删。
 */
public interface InventoryTransactionMapper extends BaseMapper<InventoryTransaction> {

    /** 流水分页查询，带仓库名 / 库位编码 / 商品编码名称 */
    IPage<InventoryTransactionVO> selectTransactionPage(IPage<InventoryTransactionVO> page,
                                                        @Param("query") TransactionQuery query);
}