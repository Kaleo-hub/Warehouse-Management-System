package com.myproject.wmsserver.web;

import com.myproject.wmsserver.common.context.UserContext;
import com.myproject.wmsserver.common.exception.AuthErrorCode;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.util.JwtUtil;
import com.myproject.wmsserver.system.service.AuthService;
import com.myproject.wmsserver.system.vo.SysUserVO;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HEADER);
        if (!StringUtils.hasText(header) || !header.startsWith(PREFIX)) {
            throw new BizException(AuthErrorCode.UNAUTHORIZED);
        }

        String token = header.substring(PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new BizException(AuthErrorCode.UNAUTHORIZED);
        }

        // 过期抛 20002，签名错抛 20001，都在 JwtUtil 里处理好了
        Claims claims = jwtUtil.parseToken(token);

        Long userId = jwtUtil.getUserId(claims);
        SysUserVO currentUser = authService.loadAuthenticatedUser(userId);
        String username = currentUser.getUsername();
        String role = currentUser.getRole();

        UserContext.set(new UserContext.CurrentUser(userId, username, role));

        /*
         * 再把身份同步一份到 Spring Security 的 SecurityContext。
         *
         * 【为什么要有这一步】@PreAuthorize 读的是 SecurityContext，不是我们的 UserContext
         *（UserContext 是给业务代码取「谁在操作」用的，两者用途不同）。
         * 不放进去的话，所有 @PreAuthorize 都会判成「匿名用户」，全部 403。
         *
         * 注意权限名要加 ROLE_ 前缀：@PreAuthorize("hasRole('ADMIN')") 里
         * Spring 会自动补上前缀去匹配 ROLE_ADMIN。
         */
        List<GrantedAuthority> authorities = StringUtils.hasText(role)
                ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                : List.of();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(username, null, authorities);
        authentication.setDetails(userId);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 必须清！Tomcat 复用线程，不清的话下一个请求会读到上一个用户的身份
        UserContext.clear();
        // SecurityContext 同理：不清会把上一个用户的角色留给下一个请求
        SecurityContextHolder.clearContext();
    }
}
