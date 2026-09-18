package com.myproject.wmsserver.common.util;

import com.myproject.wmsserver.common.exception.AuthErrorCode;
import com.myproject.wmsserver.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    @Value("${wms.jwt.secret}")
    private String secret;

    @Value("${wms.jwt.expire-minutes}")
    private long expireMinutes;

    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        // HS256 要求密钥至少 32 字节，短了 jjwt 直接抛 WeakKeyException
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "wms.jwt.secret 至少需要 32 字节，当前只有 " + bytes.length + " 字节");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        log.info("JwtUtil 初始化完成，token 有效期 {} 分钟", expireMinutes);
    }

    /** 生成 token：把 userId 放 subject，username / role 放自定义 claim */
    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireMinutes * 60 * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    /** 解析 token。过期和非法会抛不同的业务异常 */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BizException(AuthErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("token 解析失败: {}", e.getMessage());
            throw new BizException(AuthErrorCode.UNAUTHORIZED);
        }
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String getUsername(Claims claims) {
        return claims.get(CLAIM_USERNAME, String.class);
    }

    public String getRole(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }

}
