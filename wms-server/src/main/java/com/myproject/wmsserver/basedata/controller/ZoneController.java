package com.myproject.wmsserver.basedata.controller;

import com.myproject.wmsserver.basedata.dto.ZoneQuery;
import com.myproject.wmsserver.basedata.dto.ZoneSaveDTO;
import com.myproject.wmsserver.basedata.service.ZoneService;
import com.myproject.wmsserver.basedata.vo.ZoneVO;
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
@RequestMapping("/api/v1/basedata/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    /** 分页查询 */
    @GetMapping
    public Result<PageResult<ZoneVO>> page(@Valid ZoneQuery query) {
        return Result.success(zoneService.page(query));
    }

    /** 新增 */
    /*
     * 主数据维护属于「业务管理」：ADMIN / MANAGER 可以增删改，OPERATOR 只能查。
     * 角色分工见 system/enums/UserRole。
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Long> create(@Valid @RequestBody ZoneSaveDTO dto) {
        return Result.success(zoneService.create(dto));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public Result<ZoneVO> get(@PathVariable Long id) {
        return Result.success(zoneService.get(id));
    }

    /** 修改 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ZoneSaveDTO dto) {
        zoneService.update(id, dto);
        return Result.success();
    }

    /** 删除（逻辑删除） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Void> delete(@PathVariable Long id) {
        zoneService.delete(id);
        return Result.success();
    }
}