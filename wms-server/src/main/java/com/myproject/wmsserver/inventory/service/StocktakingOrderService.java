package com.myproject.wmsserver.inventory.service;

import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.inventory.dto.StocktakingCountDTO;
import com.myproject.wmsserver.inventory.dto.StocktakingOrderCreateDTO;
import com.myproject.wmsserver.inventory.dto.StocktakingOrderQuery;
import com.myproject.wmsserver.inventory.vo.StocktakingOrderVO;

/** 盘点单：建单快照、逐行录入、批量过账。 */
public interface StocktakingOrderService {

    PageResult<StocktakingOrderVO> page(StocktakingOrderQuery query);

    Long create(StocktakingOrderCreateDTO dto);

    StocktakingOrderVO get(Long id);

    void saveCounts(Long id, StocktakingCountDTO dto);

    void post(Long id);

    void cancel(Long id);
}