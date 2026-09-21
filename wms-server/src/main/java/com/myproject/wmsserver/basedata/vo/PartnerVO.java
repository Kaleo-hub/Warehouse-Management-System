package com.myproject.wmsserver.basedata.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 往来单位出参 */
@Data
public class PartnerVO {

    private Long id;

    private String partnerType;

    private String code;

    private String name;

    private String contactName;

    private String contactPhone;

    private String address;

    private String status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}