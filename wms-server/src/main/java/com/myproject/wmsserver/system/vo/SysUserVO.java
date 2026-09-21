package com.myproject.wmsserver.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户出参。
 * 刻意没有 password 字段 —— 密码哈希永远不出现在任何接口响应里。
 */
@Data
public class SysUserVO {

    private Long id;

    private String username;

    private String realName;

    private String role;

    /** 1 启用 0 禁用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}