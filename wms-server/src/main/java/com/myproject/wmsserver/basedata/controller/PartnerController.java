package com.myproject.wmsserver.basedata.controller;

import com.myproject.wmsserver.basedata.dto.PartnerQuery;
import com.myproject.wmsserver.basedata.dto.PartnerSaveDTO;
import com.myproject.wmsserver.basedata.service.PartnerService;
import com.myproject.wmsserver.basedata.vo.PartnerVO;
import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 往来单位（供应商 / 客户）接口。
 *
 * 【为什么只有一个「往来单位」而不是两个模块】
 * 字段几乎一样，用 partner_type 区分即可；真出现「供应商有账期、客户有信用额度」
 * 这类分化时再拆表也不迟。
 *
 * 权限和基础数据一致：查谁都能查，增删改要 ADMIN / MANAGER。
 */
@RestController
@RequestMapping("/api/v1/basedata/partners")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService partnerService;

    /** 分页：keyword（编码/名称/联系人）、partnerType、status */
    @GetMapping
    public Result<PageResult<PartnerVO>> page(@Validated PartnerQuery query) {
        return Result.success(partnerService.page(query));
    }

    @GetMapping("/{id}")
    public Result<PartnerVO> get(@PathVariable Long id) {
        return Result.success(partnerService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Long> create(@Valid @RequestBody PartnerSaveDTO dto) {
        return Result.success(partnerService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PartnerSaveDTO dto) {
        partnerService.update(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Result<Void> delete(@PathVariable Long id) {
        partnerService.delete(id);
        return Result.success();
    }
}