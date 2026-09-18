package com.myproject.wmsserver.system.controller;

import com.myproject.wmsserver.common.result.Result;
import com.myproject.wmsserver.system.dto.LoginDTO;
import com.myproject.wmsserver.system.service.AuthService;
import com.myproject.wmsserver.system.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @GetMapping("/me")
    public Result<LoginVO> me() {
        return Result.success(authService.me());
    }

}
