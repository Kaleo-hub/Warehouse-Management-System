package com.myproject.wmsserver.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.myproject.wmsserver.common.context.UserContext;
import com.myproject.wmsserver.common.exception.AuthErrorCode;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.util.JwtUtil;
import com.myproject.wmsserver.system.dto.LoginDTO;
import com.myproject.wmsserver.system.entity.SysUser;
import com.myproject.wmsserver.system.mapper.SysUserMapper;
import com.myproject.wmsserver.system.service.AuthService;
import com.myproject.wmsserver.system.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, dto.getUsername()));

        // 用户不存在 和 密码错，返回同一个错误码 —— 否则可以拿它枚举账号
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            log.warn("登录失败 username={}", dto.getUsername());
            throw new BizException(AuthErrorCode.BAD_CREDENTIALS);
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(AuthErrorCode.ACCOUNT_DISABLED);
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        log.info("登录成功 userId={} username={}", user.getId(), user.getUsername());

        return LoginVO.builder()
                .token(token)
                .tokenType("Bearer")
                .userInfo(buildUserInfo(user))
                .build();
    }

    @Override
    public LoginVO me() {
        // 身份由 JwtInterceptor 校验后放进 UserContext，这里只需拿 userId 查库，
        // 返回最新数据（realName/role/status 可能已变，token 里的快照不可信）
        SysUser user = sysUserMapper.selectById(UserContext.getUserId());
        if (user == null) {
            // 逻辑删除或用户不存在
            throw new BizException(AuthErrorCode.UNAUTHORIZED);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(AuthErrorCode.ACCOUNT_DISABLED);
        }

        return LoginVO.builder()
                .userInfo(buildUserInfo(user))
                .build();
    }

    private LoginVO.UserInfoVO buildUserInfo(SysUser user) {
        return LoginVO.UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .role(user.getRole())
                .build();
    }

}
