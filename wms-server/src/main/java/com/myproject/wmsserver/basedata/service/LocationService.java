package com.myproject.wmsserver.basedata.service;

import com.myproject.wmsserver.basedata.dto.LocationQuery;
import com.myproject.wmsserver.basedata.dto.LocationSaveDTO;
import com.myproject.wmsserver.basedata.vo.LocationVO;
import com.myproject.wmsserver.common.result.PageResult;

public interface LocationService {

    /** 分页查询：keyword 模糊匹配 code/name，warehouseId/zoneId/status 等值过滤，VO 里带 warehouseName/zoneName */
    PageResult<LocationVO> page(LocationQuery query);

    /** 详情，VO 里带 warehouseName/zoneName */
    LocationVO get(Long id);

    /** 新增，返回新 id */
    Long create(LocationSaveDTO dto);

    /** 修改 */
    void update(Long id, LocationSaveDTO dto);

    /** 逻辑删除 */
    void delete(Long id);

    /**
     * 上架时增加库位已用容量。
     * 超过容量上限抛 30009 库位容量不足（BR-006）；capacity = 0 表示不限。
     *
     * 提供给入库 / 出库模块在完成上架时调用 —— 跨模块只能调 Service，不能注入别人的 Mapper。
     */
    void increaseUsedCapacity(Long locationId, int qty);

    /**
     * 货物离开库位时减少已用容量（移库转出、盘亏用）。
     *
     * 和 increaseUsedCapacity 不一样：这个方法【不抛异常】。
     * 因为已用容量只是给看板看的冗余计数，一旦它自己算错了，
     * 不应该把「移库」这种正经业务操作一起卡死。
     * 计数不够减时返回 false，调用方记一条 warn 日志即可。
     *
     * @return true 减成功；false 库位不存在或已用容量不够减（未做任何修改）
     */
    boolean decreaseUsedCapacity(Long locationId, int qty);
}