package com.myproject.wmsserver.inventory.service;

import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.inventory.dto.TransferOrderCreateDTO;
import com.myproject.wmsserver.inventory.dto.TransferOrderQuery;
import com.myproject.wmsserver.inventory.vo.TransferOrderVO;

/** 移库单：一张单在同一仓库的两个库位之间移动多条 SKU / 批次。 */
public interface TransferOrderService {

    PageResult<TransferOrderVO> page(TransferOrderQuery query);

    Long create(TransferOrderCreateDTO dto);

    TransferOrderVO get(Long id);

    void complete(Long id);

    void cancel(Long id);
}