package com.myproject.wmsserver.basedata.service;

import com.myproject.wmsserver.basedata.dto.ZoneQuery;
import com.myproject.wmsserver.basedata.dto.ZoneSaveDTO;
import com.myproject.wmsserver.basedata.vo.ZoneVO;
import com.myproject.wmsserver.common.result.PageResult;

public interface ZoneService {

    /** 分页查询：keyword 模糊匹配 code/name，warehouseId/status 等值过滤，VO 里带 warehouseName */
    PageResult<ZoneVO> page(ZoneQuery query);

    /** 详情，VO 里带 warehouseName */
    ZoneVO get(Long id);

    /** 新增，返回新 id */
    Long create(ZoneSaveDTO dto);

    /** 修改 */
    void update(Long id, ZoneSaveDTO dto);

    /** 逻辑删除 */
    void delete(Long id);
}