package com.myproject.wmsserver.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建 / 修改用户。
 *
 * password 只在【新建】时必填；修改资料时不传密码（改密码走单独接口），
 * 所以这里不加 @NotBlank，由 Service 按场景判断。
 */
@Data
public class UserSaveDTO {

    /** 登录名。新建后不可改 —— 它是账号，不是昵称 */
    @NotBlank(message = "登录名不能为空")
    @Size(min = 3, max = 50, message = "登录名长度需在 3 到 50 之间")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "登录名只能包含字母、数字、下划线、点和横线")
    private String username;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名最长 50 位")
    private String realName;

    @NotBlank(message = "角色不能为空")
    private String role;

    /** 1 启用 0 禁用，不传默认启用 */
    private Integer status;

    /** 初始密码，仅新建时使用 */
    @Size(min = 6, max = 32, message = "密码长度需在 6 到 32 之间")
    private String password;
}