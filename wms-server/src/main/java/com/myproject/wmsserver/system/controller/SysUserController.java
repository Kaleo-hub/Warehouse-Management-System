package com.myproject.wmsserver.system.controller;

import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.common.result.Result;
import com.myproject.wmsserver.system.dto.PasswordDTO;
import com.myproject.wmsserver.system.dto.UserQuery;
import com.myproject.wmsserver.system.dto.UserSaveDTO;
import com.myproject.wmsserver.system.service.SysUserService;
import com.myproject.wmsserver.system.vo.SysUserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口。
 *
 * 【权限卡在类上，不是每个方法上】整个模块只有管理员能用，
 * 写在类级别上，以后加方法不用再记得补注解 —— 忘了补才是最容易出事的地方。
 *
 * 【为什么没有「查密码」接口】密码存的是 BCrypt 哈希，单向不可逆，
 * 谁都查不出来。忘记密码只能由管理员重置，这是设计使然，不是功能缺失。
 */
@RestController
@RequestMapping("/api/v1/system/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SysUserController {

    private final SysUserService sysUserService;

    /** 用户分页：keyword 匹配登录名 / 姓名 */
    @GetMapping
    public Result<PageResult<SysUserVO>> page(@Validated UserQuery query) {
        return Result.success(sysUserService.page(query));
    }

    /** 新建用户，data 返回新用户 id */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody UserSaveDTO dto) {
        return Result.success(sysUserService.create(dto));
    }

    /** 修改姓名 / 角色 / 状态（登录名不可改） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserSaveDTO dto) {
        sysUserService.update(id, dto);
        return Result.success();
    }

    /** 重置密码：管理员不需要知道旧密码 */
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordDTO dto) {
        sysUserService.resetPassword(id, dto);
        return Result.success();
    }

    /** 删除用户（逻辑删除）。不能删自己，也不能删掉最后一个启用的管理员 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.delete(id);
        return Result.success();
    }
}