package com.myproject.wmsserver.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myproject.wmsserver.basedata.dto.PartnerQuery;
import com.myproject.wmsserver.basedata.dto.PartnerSaveDTO;
import com.myproject.wmsserver.basedata.entity.Partner;
import com.myproject.wmsserver.basedata.enums.PartnerType;
import com.myproject.wmsserver.basedata.mapper.PartnerMapper;
import com.myproject.wmsserver.basedata.service.PartnerService;
import com.myproject.wmsserver.basedata.vo.PartnerVO;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.CommonErrorCode;
import com.myproject.wmsserver.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerServiceImpl implements PartnerService {

    private final PartnerMapper partnerMapper;

    @Override
    public PageResult<PartnerVO> page(PartnerQuery query) {
        Page<Partner> page = partnerMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                Wrappers.<Partner>lambdaQuery()
                        .and(StringUtils.hasText(query.getKeyword()), w -> w
                                .like(Partner::getCode, query.getKeyword())
                                .or().like(Partner::getName, query.getKeyword())
                                .or().like(Partner::getContactName, query.getKeyword()))
                        .eq(StringUtils.hasText(query.getPartnerType()),
                                Partner::getPartnerType, query.getPartnerType())
                        .eq(StringUtils.hasText(query.getStatus()), Partner::getStatus, query.getStatus())
                        .orderByDesc(Partner::getId));

        List<PartnerVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(page, records);
    }

    @Override
    public PartnerVO get(Long id) {
        return toVO(requireExists(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PartnerSaveDTO dto) {
        checkTypeValid(dto.getPartnerType());
        checkCodeUnique(dto.getCode(), null);

        Partner partner = new Partner();
        BeanUtils.copyProperties(dto, partner);
        partnerMapper.insert(partner);
        log.info("新建往来单位 id={} type={} code={} name={}",
                partner.getId(), partner.getPartnerType(), partner.getCode(), partner.getName());
        return partner.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PartnerSaveDTO dto) {
        Partner partner = requireExists(id);
        checkTypeValid(dto.getPartnerType());
        checkCodeUnique(dto.getCode(), id);

        BeanUtils.copyProperties(dto, partner);
        partner.setId(id);
        partnerMapper.updateById(partner);
        log.info("修改往来单位 id={} code={}", id, partner.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireExists(id);
        /*
         * 这里【没有】拦「已经被单据引用的往来单位」。
         * 因为单据存的是名称快照 + 可空 id：删掉主数据不影响历史单据的显示，
         * 而且逻辑删除之后 id 依然存在，统计时也还能对上。
         * 真需要「有业务引用就不许删」时再加校验，但那会让主数据维护变得很难受。
         */
        partnerMapper.deleteById(id);
        log.info("删除往来单位 id={}", id);
    }

    private Partner requireExists(Long id) {
        Partner partner = partnerMapper.selectById(id);
        if (partner == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        return partner;
    }

    private void checkTypeValid(String type) {
        if (!PartnerType.isValid(type)) {
            log.warn("非法往来单位类型 type={}", type);
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
    }

    /** 编码全局唯一。excludeId 为 null 表示新增 */
    private void checkCodeUnique(String code, Long excludeId) {
        Long count = partnerMapper.selectCount(Wrappers.<Partner>lambdaQuery()
                .eq(Partner::getCode, code)
                .ne(excludeId != null, Partner::getId, excludeId));
        if (count != null && count > 0) {
            log.warn("往来单位编码重复 code={}", code);
            throw new BizException(CommonErrorCode.DATA_ALREADY_EXISTS);
        }
    }

    private PartnerVO toVO(Partner partner) {
        PartnerVO vo = new PartnerVO();
        BeanUtils.copyProperties(partner, vo);
        return vo;
    }
}