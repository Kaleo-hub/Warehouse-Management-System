package com.myproject.wmsserver.basedata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 库区。表结构见 db/migration/V1.0.0__init_schema.sql */
@Data
@TableName("zone")
public class Zone {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属仓库 ID */
    private Long warehouseId;

    /** 库区编码，同一仓库下唯一，≤32 */
    private String code;

    /** 库区名称，≤64 */
    private String name;

    /** 类型：STORAGE / PICKING / RECEIVING / SHIPPING / SORTING */
    private String zoneType;

    /** 排序号 */
    private Integer sortNo;

    /** 状态：ENABLED / DISABLED */
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