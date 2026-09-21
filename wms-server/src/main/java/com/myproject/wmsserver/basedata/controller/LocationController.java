package com.myproject.wmsserver.basedata.controller;

import com.myproject.wmsserver.basedata.dto.LocationQuery;
import com.myproject.wmsserver.basedata.dto.LocationSaveDTO;
import com.myproject.wmsserver.basedata.service.LocationService;
import com.myproject.wmsserver.basedata.vo.LocationVO;
import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/basedata/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    /** 分页查询 */
    @GetMapping
    public Result<PageResult<LocationVO>> page(@Valid LocationQuery query) {
        return Result.success(locationService.page(query));
    }

    /** 新增 */
    /*
     * 主数据维护属于「业务管理」：ADMIN / MANAGER 可以增删改，OPERATOR 只能查。
     * 角色分工见 system/enums/UserRole。
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Long> create(@Valid @RequestBody LocationSaveDTO dto) {
        return Result.success(locationService.create(dto));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public Result<LocationVO> get(@PathVariable Long id) {
        return Result.success(locationService.get(id));
    }

    /** 修改 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody LocationSaveDTO dto) {
        locationService.update(id, dto);
        return Result.success();
    }

    /** 删除（逻辑删除） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Void> delete(@PathVariable Long id) {
        locationService.delete(id);
        return Result.success();
    }
}