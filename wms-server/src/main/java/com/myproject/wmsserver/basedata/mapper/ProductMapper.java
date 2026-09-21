package com.myproject.wmsserver.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myproject.wmsserver.basedata.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductMapper extends BaseMapper<Product> {

    /** 只读投影：商品是否还有非零库存，删除前做引用校验。 */
    @Select("SELECT COUNT(*) FROM inventory WHERE sku_id = #{skuId} AND deleted = 0 "
            + "AND (qty > 0 OR locked_qty > 0 OR frozen_qty > 0)")
    int countInventoryBySkuId(@Param("skuId") Long skuId);
}