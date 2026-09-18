package com.myproject.wmsserver.basedata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 库位。表结构见 db/migration/V1.0.0__init_schema.sql */
@Data
@TableName("location")
public class Location {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属仓库 ID */
    private Long warehouseId;

    /** 所属库区 ID */
    private Long zoneId;

    /** 库位编码，同一仓库下唯一，≤64 */
    private String code;

    /** 库位名称，≤64 */
    private String name;

    /** 类型：RACK / FLOOR / TEMP */
    private String locationType;

    /** 容量上限（件），0 表示不限 */
    private Integer capacity;

    /** 已用容量（件），与库存上架同事务维护，不由前端写 */
    private Integer usedCapacity;

    /** 状态：ENABLED / DISABLED / FROZEN（建表注释含 FROZEN，接口文档暂只暴露前两者） */
    private String status;

    /** 备注，≤255 */
    private String remark;

    /** 逻辑删除：0 未删除，1 已删除 */
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createBy;

    private Long updateBy;
}