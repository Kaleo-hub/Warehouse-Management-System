package com.myproject.wmsserver.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myproject.wmsserver.basedata.entity.Location;
import org.apache.ibatis.annotations.Param;

public interface LocationMapper extends BaseMapper<Location> {

    /**
     * 上架时增加库位已用容量，带容量校验（BR-006）。
     *
     * 条件里的 (capacity = 0 OR used_capacity + qty &lt;= capacity) 是关键：
     * 把「判断能不能放」和「增加占用」合成一条原子 SQL，
     * 并发上架到同一个库位时不会两个都通过检查然后超容。
     *
     * capacity = 0 表示不限容量（临时暂存位）。
     *
     * @return 影响行数。0 表示容量不足或库位不存在
     */
    int increaseUsedCapacity(@Param("id") Long id, @Param("qty") int qty);

    /**
     * 货物移出库位（移库转出 / 盘亏）时减少已用容量。
     *
     * 条件 used_capacity &gt;= qty 是防负数：已用容量是冗余计数，
     * 万一被手工改过或其他模块漏减，这里宁可少减也不要把计数写成负数。
     *
     * @return 影响行数。0 表示库位不存在或已用容量不够减
     */
    int decreaseUsedCapacity(@Param("id") Long id, @Param("qty") int qty);
}