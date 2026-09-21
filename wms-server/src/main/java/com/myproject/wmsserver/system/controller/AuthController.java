package com.myproject.wmsserver.system.controller;

import com.myproject.wmsserver.common.result.Result;
import com.myproject.wmsserver.system.dto.LoginDTO;
import com.myproject.wmsserver.system.dto.PasswordDTO;
import com.myproject.wmsserver.system.service.AuthService;
import com.myproject.wmsserver.system.service.SysUserService;
import com.myproject.wmsserver.system.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SysUserService sysUserService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @GetMapping("/me")
    public Result<LoginVO> me() {
        return Result.success(authService.me());
    }

    /**
     * 本人修改密码。
     *
     * 没有加 @PreAuthorize —— 任何登录用户都该能改自己的密码；
     * 但必须先验证旧密码（在 Service 里做），
     * 否则拿到 token 的人就能直接改密码把号占住。
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody PasswordDTO dto) {
        sysUserService.changeOwnPassword(dto);
        return Result.success();
    }

}
