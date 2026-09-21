package com.myproject.wmsserver.system.service;

import com.myproject.wmsserver.system.dto.LoginDTO;
import com.myproject.wmsserver.system.vo.LoginVO;
import com.myproject.wmsserver.system.vo.SysUserVO;

public interface AuthService {

    LoginVO login(LoginDTO dto);

    LoginVO me();

    /** 认证请求使用最新用户信息，避免 JWT 中的角色和启用状态长期过期。 */
    SysUserVO loadAuthenticatedUser(Long userId);
}