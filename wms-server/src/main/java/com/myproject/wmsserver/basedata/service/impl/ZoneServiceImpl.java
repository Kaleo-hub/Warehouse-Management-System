package com.myproject.wmsserver.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myproject.wmsserver.basedata.dto.ZoneQuery;
import com.myproject.wmsserver.basedata.dto.ZoneSaveDTO;
import com.myproject.wmsserver.basedata.entity.Location;
import com.myproject.wmsserver.basedata.entity.Warehouse;
import com.myproject.wmsserver.basedata.entity.Zone;
import com.myproject.wmsserver.basedata.mapper.LocationMapper;
import com.myproject.wmsserver.basedata.mapper.WarehouseMapper;
import com.myproject.wmsserver.basedata.mapper.ZoneMapper;
import com.myproject.wmsserver.basedata.service.ZoneService;
import com.myproject.wmsserver.basedata.vo.ZoneVO;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.CommonErrorCode;
import com.myproject.wmsserver.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 库区 CRUD —— 模式参考 WarehouseServiceImpl（基础数据参考实现）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneServiceImpl implements ZoneService {

    private final ZoneMapper zoneMapper;
    private final WarehouseMapper warehouseMapper;
    private final LocationMapper locationMapper;

    @Override
    public PageResult<ZoneVO> page(ZoneQuery query) {
        Page<Zone> page = zoneMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                Wrappers.<Zone>lambdaQuery()
                        // keyword 模糊匹配 code/name 两列
                        .and(StringUtils.hasText(query.getKeyword()), w -> w
                                .like(Zone::getCode, query.getKeyword())
                                .or().like(Zone::getName, query.getKeyword()))
                        .eq(query.getWarehouseId() != null, Zone::getWarehouseId, query.getWarehouseId())
                        .eq(StringUtils.hasText(query.getStatus()), Zone::getStatus, query.getStatus())
                        .orderByDesc(Zone::getId));

        List<ZoneVO> records = page.getRecords().stream().map(this::toVO).toList();
        // warehouseName 冗余字段：分页查完后批量补查 warehouse 再填充，zone 数量不大，比 join 简单
        fillWarehouseNames(records);
        return PageResult.of(page, records);
    }

    @Override
    public ZoneVO get(Long id) {
        Zone zone = requireExists(id);
        ZoneVO vo = toVO(zone);
        Warehouse warehouse = warehouseMapper.selectById(zone.getWarehouseId());
        if (warehouse != null) {
            vo.setWarehouseName(warehouse.getName());
        }
        return vo;
    }

    @Override
    public Long create(ZoneSaveDTO dto) {
        checkWarehouseExists(dto.getWarehouseId());
        checkCodeUnique(dto.getWarehouseId(), dto.getCode(), null);

        Zone zone = new Zone();
        BeanUtils.copyProperties(dto, zone);
        zoneMapper.insert(zone);
        log.info("新增库区 id={} code={}", zone.getId(), dto.getCode());
        return zone.getId();
    }

    @Override
    public void update(Long id, ZoneSaveDTO dto) {
        Zone existing = requireExists(id);
        checkWarehouseExists(dto.getWarehouseId());
        if (!existing.getWarehouseId().equals(dto.getWarehouseId())
                && locationMapper.exists(Wrappers.<Location>lambdaQuery().eq(Location::getZoneId, id))) {
            throw new BizException(CommonErrorCode.RELATED_DATA_EXISTS);
        }
        checkCodeUnique(dto.getWarehouseId(), dto.getCode(), id);

        Zone zone = new Zone();
        BeanUtils.copyProperties(dto, zone);
        zone.setId(id);
        zoneMapper.updateById(zone);
        log.info("修改库区 id={}", id);
    }

    @Override
    public void delete(Long id) {
        requireExists(id);
        // 关联数据校验：库区下还有库位时拒绝删除（接口文档 6.4），避免库位失去归属
        if (locationMapper.exists(Wrappers.<Location>lambdaQuery().eq(Location::getZoneId, id))) {
            log.warn("库区下存在库位，拒绝删除 zoneId={}", id);
            throw new BizException(CommonErrorCode.RELATED_DATA_EXISTS);
        }
        // 全局 logic-delete 配置生效，deleteById 自动转成 update deleted=1
        zoneMapper.deleteById(id);
        log.info("删除库区 id={}", id);
    }

    /** 按 id 查，不存在抛 RESOURCE_NOT_FOUND */
    private Zone requireExists(Long id) {
        Zone zone = zoneMapper.selectById(id);
        if (zone == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        return zone;
    }

    /** 业务校验：所属仓库必须存在（开发规范 4.6：存在性校验放 Service） */
    private void checkWarehouseExists(Long warehouseId) {
        if (warehouseMapper.selectById(warehouseId) == null) {
            log.warn("所属仓库不存在 warehouseId={}", warehouseId);
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
    }

    /**
     * 库区编码在仓库内唯一。excludeId 为 null 表示新增；非 null 表示修改（排除自己）。
     * 建表 uk_wh_code 唯一索引兜底，并发穿透这里时由 GlobalExceptionHandler 的
     * DuplicateKeyException 处理转成 DATA_ALREADY_EXISTS。
     */
    private void checkCodeUnique(Long warehouseId, String code, Long excludeId) {
        boolean exists = zoneMapper.exists(Wrappers.<Zone>lambdaQuery()
                .eq(Zone::getWarehouseId, warehouseId)
                .eq(Zone::getCode, code)
                .ne(excludeId != null, Zone::getId, excludeId));
        if (exists) {
            log.warn("库区编码重复 warehouseId={} code={}", warehouseId, code);
            throw new BizException(CommonErrorCode.DATA_ALREADY_EXISTS);
        }
    }

    private ZoneVO toVO(Zone zone) {
        ZoneVO vo = new ZoneVO();
        BeanUtils.copyProperties(zone, vo);
        return vo;
    }

    /** 批量补查 warehouse，填充 warehouseName 冗余字段 */
    private void fillWarehouseNames(List<ZoneVO> records) {
        if (records.isEmpty()) {
            return;
        }
        Set<Long> warehouseIds = records.stream()
                .map(ZoneVO::getWarehouseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (warehouseIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = warehouseMapper.selectBatchIds(warehouseIds).stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
        records.forEach(vo -> vo.setWarehouseName(nameMap.get(vo.getWarehouseId())));
    }
}
