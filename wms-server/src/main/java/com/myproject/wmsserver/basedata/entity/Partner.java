package com.myproject.wmsserver.basedata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 往来单位（供应商 / 客户）。表结构见 V1.0.4 */
@Data
@TableName("partner")
public class Partner {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** PartnerType 枚举名：SUPPLIER / CUSTOMER */
    private String partnerType;

    /** 编码，全局唯一 */
    private String code;

    private String name;

    private String contactName;

    private String contactPhone;

    private String address;

    /** ENABLED / DISABLED */
    private String status;

    private String remark;

    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createBy;

    private Long updateBy;
}