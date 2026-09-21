package com.myproject.wmsserver.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 改密码。
 * 管理员重置别人密码时只用 newPassword；用户自己改密码时 oldPassword 也必填。
 */
@Data
public class PasswordDTO {

    /** 仅「自己改密码」时使用 */
    @Size(max = 32, message = "密码最长 32 位")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度需在 6 到 32 之间")
    private String newPassword;
}