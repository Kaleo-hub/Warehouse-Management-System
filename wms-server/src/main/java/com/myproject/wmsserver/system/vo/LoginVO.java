package com.myproject.wmsserver.system.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginVO {

    private String token;

    /** 固定 Bearer，告诉前端怎么放到请求头里 */
    private String tokenType;

    private UserInfoVO userInfo;

    @Data
    @Builder
    public static class UserInfoVO {
        private Long id;
        private String username;
        private String realName;
        private String role;
    }
}
