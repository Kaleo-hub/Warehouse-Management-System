package com.myproject.wmsserver.basedata.service;

import com.myproject.wmsserver.basedata.dto.PageQuery;
import com.myproject.wmsserver.basedata.dto.WarehouseSaveDTO;
import com.myproject.wmsserver.basedata.vo.WarehouseVO;
import com.myproject.wmsserver.common.result.PageResult;

public interface WarehouseService {

    /** 分页查询：keyword 模糊匹配 code/name/address，status 等值过滤 */
    PageResult<WarehouseVO> page(PageQuery query);

    /** 详情 */
    WarehouseVO get(Long id);

    /** 新增，返回新 id */
    Long create(WarehouseSaveDTO dto);

    /** 修改 */
    void update(Long id, WarehouseSaveDTO dto);

    /** 逻辑删除 */
    void delete(Long id);
}