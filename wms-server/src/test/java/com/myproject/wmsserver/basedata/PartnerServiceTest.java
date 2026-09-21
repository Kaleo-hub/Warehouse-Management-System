package com.myproject.wmsserver.basedata;

import com.myproject.wmsserver.basedata.dto.PartnerQuery;
import com.myproject.wmsserver.basedata.dto.PartnerSaveDTO;
import com.myproject.wmsserver.basedata.service.PartnerService;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.CommonErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 往来单位主数据的验收测试。
 *
 * 用 @Transactional 让它跑完整卷回 —— 这类纯 CRUD 测试不需要验事务边界，
 * 卷回比手工清理更干净。
 */
@SpringBootTest
@Transactional
class PartnerServiceTest {

    private static final String CODE = "T-PARTNER-001";

    @Autowired
    private PartnerService partnerService;

    private PartnerSaveDTO dto(String code, String type) {
        PartnerSaveDTO dto = new PartnerSaveDTO();
        dto.setPartnerType(type);
        dto.setCode(code);
        dto.setName("测试单位 " + code);
        dto.setContactName("王经理");
        dto.setContactPhone("13800000000");
        dto.setStatus("ENABLED");
        return dto;
    }

    @Test
    @DisplayName("新增往来单位：能按类型区分供应商和客户")
    void createAndQueryByType() {
        Long supplierId = partnerService.create(dto(CODE, "SUPPLIER"));
        assertNotNull(supplierId);

        PartnerQuery query = new PartnerQuery();
        query.setPartnerType("SUPPLIER");
        query.setKeyword(CODE);
        assertEquals(1, partnerService.page(query).getTotal(), "按类型应该能筛出来");

        PartnerQuery customerQuery = new PartnerQuery();
        customerQuery.setPartnerType("CUSTOMER");
        customerQuery.setKeyword(CODE);
        assertEquals(0, partnerService.page(customerQuery).getTotal(), "它不是客户，按客户筛就查不到");
    }

    @Test
    @DisplayName("编码全局唯一：重复编码要被拒绝（唯一索引兜底）")
    void duplicateCodeShouldBeRejected() {
        partnerService.create(dto(CODE, "SUPPLIER"));

        BizException ex = assertThrows(BizException.class,
                () -> partnerService.create(dto(CODE, "CUSTOMER")));
        assertEquals(CommonErrorCode.DATA_ALREADY_EXISTS.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("类型必须合法：乱传类型直接拒绝")
    void invalidTypeShouldBeRejected() {
        BizException ex = assertThrows(BizException.class,
                () -> partnerService.create(dto(CODE, "UNKNOWN")));
        assertEquals(CommonErrorCode.PARAM_INVALID.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("修改：改名称不影响编码唯一性校验（排除自己）")
    void updateShouldExcludeSelf() {
        Long id = partnerService.create(dto(CODE, "SUPPLIER"));

        PartnerSaveDTO update = dto(CODE, "SUPPLIER");
        update.setName("改过名字的测试单位");
        partnerService.update(id, update);

        assertEquals("改过名字的测试单位", partnerService.get(id).getName());
    }
}