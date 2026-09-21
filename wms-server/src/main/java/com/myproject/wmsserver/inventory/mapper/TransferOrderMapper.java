package com.myproject.wmsserver.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.myproject.wmsserver.inventory.dto.TransferOrderQuery;
import com.myproject.wmsserver.inventory.entity.TransferOrder;
import com.myproject.wmsserver.inventory.vo.TransferOrderVO;
import org.apache.ibatis.annotations.Param;

public interface TransferOrderMapper extends BaseMapper<TransferOrder> {

    IPage<TransferOrderVO> selectOrderPage(IPage<TransferOrderVO> page,
                                          @Param("query") TransferOrderQuery query);

    TransferOrderVO selectOrderDetail(@Param("id") Long id);

    TransferOrder selectForUpdate(@Param("id") Long id);

    int markCompleted(@Param("id") Long id, @Param("updateBy") Long updateBy);

    int cancel(@Param("id") Long id, @Param("updateBy") Long updateBy);
}