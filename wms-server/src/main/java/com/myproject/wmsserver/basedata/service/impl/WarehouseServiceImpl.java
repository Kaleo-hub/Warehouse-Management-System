package com.myproject.wmsserver.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myproject.wmsserver.basedata.dto.PageQuery;
import com.myproject.wmsserver.basedata.dto.WarehouseSaveDTO;
import com.myproject.wmsserver.basedata.entity.Warehouse;
import com.myproject.wmsserver.basedata.entity.Zone;
import com.myproject.wmsserver.basedata.mapper.WarehouseMapper;
import com.myproject.wmsserver.basedata.mapper.ZoneMapper;
import com.myproject.wmsserver.basedata.service.WarehouseService;
import com.myproject.wmsserver.basedata.vo.WarehouseVO;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.CommonErrorCode;
import com.myproject.wmsserver.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 仓库 CRUD —— 基础数据四个模块的参考实现，其余三个模块照这个模式写即可。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseMapper warehouseMapper;
    private final ZoneMapper zoneMapper;

    @Override
    public PageResult<WarehouseVO> page(PageQuery query) {
        Page<Warehouse> page = warehouseMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                Wrappers.<Warehouse>lambdaQuery()
                        // keyword 模糊匹配 code/name/address 三列
                        .and(StringUtils.hasText(query.getKeyword()), w -> w
                                .like(Warehouse::getCode, query.getKeyword())
                                .or().like(Warehouse::getName, query.getKeyword())
                                .or().like(Warehouse::getAddress, query.getKeyword()))
                        .eq(StringUtils.hasText(query.getStatus()), Warehouse::getStatus, query.getStatus())
                        .orderByDesc(Warehouse::getId));

        return PageResult.of(page, page.getRecords().stream().map(this::toVO).toList());
    }

    @Override
    public WarehouseVO get(Long id) {
        return toVO(requireExists(id));
    }

    @Override
    public Long create(WarehouseSaveDTO dto) {
        checkCodeUnique(dto.getCode(), null);

        Warehouse warehouse = new Warehouse();
        BeanUtils.copyProperties(dto, warehouse);
        warehouseMapper.insert(warehouse);
        log.info("新增仓库 id={} code={}", warehouse.getId(), dto.getCode());
        return warehouse.getId();
    }

    @Override
    public void update(Long id, WarehouseSaveDTO dto) {
        requireExists(id);
        checkCodeUnique(dto.getCode(), id);

        Warehouse warehouse = new Warehouse();
        BeanUtils.copyProperties(dto, warehouse);
        warehouse.setId(id);
        warehouseMapper.updateById(warehouse);
        log.info("修改仓库 id={}", id);
    }

    @Override
    public void delete(Long id) {
        requireExists(id);
        // 关联数据校验：仓库下还有库区时拒绝删除（接口文档 6.4），避免库区失去归属
        if (zoneMapper.exists(Wrappers.<Zone>lambdaQuery().eq(Zone::getWarehouseId, id))) {
            log.warn("仓库下存在库区，拒绝删除 warehouseId={}", id);
            throw new BizException(CommonErrorCode.RELATED_DATA_EXISTS);
        }
        // 全局 logic-delete 配置生效，deleteById 自动转成 update deleted=1
        warehouseMapper.deleteById(id);
        log.info("删除仓库 id={}", id);
    }

    /** 按 id 查，不存在抛 RESOURCE_NOT_FOUND */
    private Warehouse requireExists(Long id) {
        Warehouse warehouse = warehouseMapper.selectById(id);
        if (warehouse == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        return warehouse;
    }

    /**
     * code 全局唯一。excludeId 为 null 表示新增；非 null 表示修改（排除自己）。
     * 建表 uk_code 唯一索引兜底，并发穿透这里时由 GlobalExceptionHandler 的
     * DuplicateKeyException 处理转成 DATA_ALREADY_EXISTS。
     */
    private void checkCodeUnique(String code, Long excludeId) {
        boolean exists = warehouseMapper.exists(Wrappers.<Warehouse>lambdaQuery()
                .eq(Warehouse::getCode, code)
                .ne(excludeId != null, Warehouse::getId, excludeId));
        if (exists) {
            log.warn("仓库编码重复 code={}", code);
            throw new BizException(CommonErrorCode.DATA_ALREADY_EXISTS);
        }
    }

    private WarehouseVO toVO(Warehouse warehouse) {
        WarehouseVO vo = new WarehouseVO();
        BeanUtils.copyProperties(warehouse, vo);
        return vo;
    }
}