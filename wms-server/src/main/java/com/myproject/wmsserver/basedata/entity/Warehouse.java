package com.myproject.wmsserver.basedata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 仓库。表结构见 db/migration/V1.0.0__init_schema.sql */
@Data
@TableName("warehouse")
public class Warehouse {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 仓库编码，全局唯一，≤32 */
    private String code;

    /** 仓库名称，≤64 */
    private String name;

    /** 仓库地址，≤255 */
    private String address;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 备注，≤255 */
    private String remark;

    /** 逻辑删除：0 未删除，1 已删除（全局 logic-delete 配置生效） */
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createBy;

    private Long updateBy;
}