package com.myproject.wmsserver.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myproject.wmsserver.basedata.dto.LocationQuery;
import com.myproject.wmsserver.basedata.dto.LocationSaveDTO;
import com.myproject.wmsserver.basedata.entity.Location;
import com.myproject.wmsserver.basedata.entity.Warehouse;
import com.myproject.wmsserver.basedata.entity.Zone;
import com.myproject.wmsserver.basedata.mapper.LocationMapper;
import com.myproject.wmsserver.basedata.mapper.WarehouseMapper;
import com.myproject.wmsserver.basedata.mapper.ZoneMapper;
import com.myproject.wmsserver.basedata.service.LocationService;
import com.myproject.wmsserver.basedata.vo.LocationVO;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 库位 CRUD —— 模式参考 WarehouseServiceImpl（基础数据参考实现）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final LocationMapper locationMapper;
    private final WarehouseMapper warehouseMapper;
    private final ZoneMapper zoneMapper;

    @Override
    public PageResult<LocationVO> page(LocationQuery query) {
        Page<Location> page = locationMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                Wrappers.<Location>lambdaQuery()
                        // keyword 模糊匹配 code/name 两列
                        .and(StringUtils.hasText(query.getKeyword()), w -> w
                                .like(Location::getCode, query.getKeyword())
                                .or().like(Location::getName, query.getKeyword()))
                        .eq(query.getWarehouseId() != null, Location::getWarehouseId, query.getWarehouseId())
                        .eq(query.getZoneId() != null, Location::getZoneId, query.getZoneId())
                        .eq(StringUtils.hasText(query.getStatus()), Location::getStatus, query.getStatus())
                        .orderByDesc(Location::getId));

        List<LocationVO> records = page.getRecords().stream().map(this::toVO).toList();
        // warehouseName/zoneName 冗余字段：分页查完后批量补查再填充，比 join 简单
        fillNames(records);
        return PageResult.of(page, records);
    }

    @Override
    public LocationVO get(Long id) {
        Location location = requireExists(id);
        LocationVO vo = toVO(location);
        Warehouse warehouse = warehouseMapper.selectById(location.getWarehouseId());
        if (warehouse != null) {
            vo.setWarehouseName(warehouse.getName());
        }
        Zone zone = zoneMapper.selectById(location.getZoneId());
        if (zone != null) {
            vo.setZoneName(zone.getName());
        }
        return vo;
    }

    @Override
    public Long create(LocationSaveDTO dto) {
        checkWarehouseExists(dto.getWarehouseId());
        checkZoneBelongsToWarehouse(dto.getZoneId(), dto.getWarehouseId());
        checkCodeUnique(dto.getWarehouseId(), dto.getCode(), null);

        Location location = new Location();
        BeanUtils.copyProperties(dto, location);
        // 已用容量与库存上架同事务维护，不由前端写；新库位从 0 开始
        location.setUsedCapacity(0);
        locationMapper.insert(location);
        log.info("新增库位 id={} code={}", location.getId(), dto.getCode());
        return location.getId();
    }

    @Override
    public void update(Long id, LocationSaveDTO dto) {
        requireExists(id);
        checkWarehouseExists(dto.getWarehouseId());
        checkZoneBelongsToWarehouse(dto.getZoneId(), dto.getWarehouseId());
        checkCodeUnique(dto.getWarehouseId(), dto.getCode(), id);

        Location location = new Location();
        BeanUtils.copyProperties(dto, location);
        location.setId(id);
        // DTO 不含 usedCapacity，updateById 默认跳过 null 字段，不会被误清
        locationMapper.updateById(location);
        log.info("修改库位 id={}", id);
    }

    @Override
    public void delete(Long id) {
        Location location = requireExists(id);
        // 关联数据校验：库位上还有库存（usedCapacity > 0）时拒绝删除（接口文档 6.4）。
        // 更细的库存占用校验规则等库存模块实现后按约定补充。
        if (location.getUsedCapacity() != null && location.getUsedCapacity() > 0) {
            log.warn("库位有库存，拒绝删除 locationId={} usedCapacity={}", id, location.getUsedCapacity());
            throw new BizException(CommonErrorCode.RELATED_DATA_EXISTS);
        }
        // 全局 logic-delete 配置生效，deleteById 自动转成 update deleted=1
        locationMapper.deleteById(id);
        log.info("删除库位 id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void increaseUsedCapacity(Long locationId, int qty) {
        if (qty <= 0) {
            return;
        }
        int affected = locationMapper.increaseUsedCapacity(locationId, qty);
        if (affected == 0) {
            log.warn("库位容量不足或库位不存在 locationId={} 本次占用={}", locationId, qty);
            throw new BizException(CommonErrorCode.LOCATION_CAPACITY_NOT_ENOUGH);
        }
    }

    /** 按 id 查，不存在抛 RESOURCE_NOT_FOUND */
    private Location requireExists(Long id) {
        Location location = locationMapper.selectById(id);
        if (location == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        return location;
    }

    /** 业务校验：所属仓库必须存在（开发规范 4.6：存在性校验放 Service） */
    private void checkWarehouseExists(Long warehouseId) {
        if (warehouseMapper.selectById(warehouseId) == null) {
            log.warn("所属仓库不存在 warehouseId={}", warehouseId);
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
    }

    /** 业务校验：库区必须存在且归属该仓库，防止把库位挂到别的仓库的库区下 */
    private void checkZoneBelongsToWarehouse(Long zoneId, Long warehouseId) {
        Zone zone = zoneMapper.selectById(zoneId);
        if (zone == null || !zone.getWarehouseId().equals(warehouseId)) {
            log.warn("库区不存在或不属于该仓库 zoneId={} warehouseId={}", zoneId, warehouseId);
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
    }

    /**
     * 库位编码在仓库内唯一。excludeId 为 null 表示新增；非 null 表示修改（排除自己）。
     * 建表 uk_wh_code 唯一索引兜底，并发穿透这里时由 GlobalExceptionHandler 的
     * DuplicateKeyException 处理转成 DATA_ALREADY_EXISTS。
     */
    private void checkCodeUnique(Long warehouseId, String code, Long excludeId) {
        boolean exists = locationMapper.exists(Wrappers.<Location>lambdaQuery()
                .eq(Location::getWarehouseId, warehouseId)
                .eq(Location::getCode, code)
                .ne(excludeId != null, Location::getId, excludeId));
        if (exists) {
            log.warn("库位编码重复 warehouseId={} code={}", warehouseId, code);
            throw new BizException(CommonErrorCode.DATA_ALREADY_EXISTS);
        }
    }

    private LocationVO toVO(Location location) {
        LocationVO vo = new LocationVO();
        BeanUtils.copyProperties(location, vo);
        return vo;
    }

    /** 批量补查 warehouse / zone，填充 warehouseName / zoneName 冗余字段 */
    private void fillNames(List<LocationVO> records) {
        if (records.isEmpty()) {
            return;
        }
        Set<Long> warehouseIds = records.stream()
                .map(LocationVO::getWarehouseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> zoneIds = records.stream()
                .map(LocationVO::getZoneId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> warehouseNameMap = warehouseIds.isEmpty() ? Map.of()
                : warehouseMapper.selectBatchIds(warehouseIds).stream()
                        .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
        Map<Long, String> zoneNameMap = zoneIds.isEmpty() ? Map.of()
                : zoneMapper.selectBatchIds(zoneIds).stream()
                        .collect(Collectors.toMap(Zone::getId, Zone::getName));

        records.forEach(vo -> {
            vo.setWarehouseName(warehouseNameMap.get(vo.getWarehouseId()));
            vo.setZoneName(zoneNameMap.get(vo.getZoneId()));
        });
    }
}
