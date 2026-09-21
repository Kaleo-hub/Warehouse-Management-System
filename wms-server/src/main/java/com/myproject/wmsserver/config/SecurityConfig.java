package com.myproject.wmsserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
/*
 * 开启方法级鉴权，让 @PreAuthorize 生效。
 *
 * 【为什么不是配在 HttpSecurity 里】
 * 这个项目的登录态是 JwtInterceptor 解析 token 得到的（不是 Spring Security 的 filter chain），
 * authorizeHttpRequests 阶段还看不到用户身份。
 * 而 @PreAuthorize 是在「Controller 方法被调用前」由 AOP 校验的 ——
 * 那时候 JwtInterceptor 已经跑完、SecurityContext 已经填好了，正好能拿到角色。
 * 所以鉴权分两层：filter chain 全放行（只负责关掉不需要的默认行为），
 * 真正的鉴权在 JwtInterceptor（认证）+ @PreAuthorize（授权）。
 */
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 关掉 Spring Security 自带的那些默认行为
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 不用 session，每个请求靠 token 认人
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 全部放行 —— 真正的鉴权交给后面的 JwtInterceptor
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    /** 提供 BCrypt 密码编码器，登录校验和建用户时都用它 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
