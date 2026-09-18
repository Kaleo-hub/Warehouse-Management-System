package com.myproject.wmsserver.web;

import com.myproject.wmsserver.common.context.UserContext;
import com.myproject.wmsserver.common.exception.AuthErrorCode;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

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

        UserContext.set(new UserContext.CurrentUser(
                jwtUtil.getUserId(claims),
                jwtUtil.getUsername(claims),
                jwtUtil.getRole(claims)));

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 必须清！Tomcat 复用线程，不清的话下一个请求会读到上一个用户的身份
        UserContext.clear();
    }
}
