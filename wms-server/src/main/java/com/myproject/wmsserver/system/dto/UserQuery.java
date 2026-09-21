package com.myproject.wmsserver.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 用户列表查询条件。
 *
 * 【为什么这里不继承 PageQuery】
 * PageQuery 里的 status 是 String（ENABLED/DISABLED），而 sys_user.status 是 tinyint（1/0）。
 * 硬继承的话字段类型对不上，还得在注解和转换上绕，不如单独定义干净。
 */
@Data
public class UserQuery {

    @Min(value = 1, message = "page 不能小于 1")
    private int page = 1;

    @Min(value = 1, message = "size 不能小于 1")
    @Max(value = 100, message = "size 最大 100")
    private int size = 10;

    /** 模糊匹配 username / realName */
    private String keyword;

    /** ADMIN / MANAGER / OPERATOR，null 表示不过滤 */
    private String role;

    /** 1 启用 0 禁用，null 表示不过滤 */
    private Integer status;
}