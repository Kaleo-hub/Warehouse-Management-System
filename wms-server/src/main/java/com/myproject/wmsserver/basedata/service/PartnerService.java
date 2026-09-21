package com.myproject.wmsserver.basedata.service;

import com.myproject.wmsserver.basedata.dto.PartnerQuery;
import com.myproject.wmsserver.basedata.dto.PartnerSaveDTO;
import com.myproject.wmsserver.basedata.vo.PartnerVO;
import com.myproject.wmsserver.common.result.PageResult;

public interface PartnerService {

    PageResult<PartnerVO> page(PartnerQuery query);

    PartnerVO get(Long id);

    Long create(PartnerSaveDTO dto);

    void update(Long id, PartnerSaveDTO dto);

    void delete(Long id);
}