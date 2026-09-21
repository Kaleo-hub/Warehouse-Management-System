package com.myproject.wmsserver.basedata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新增 / 修改往来单位 */
@Data
public class PartnerSaveDTO {

    @NotBlank(message = "类型不能为空")
    private String partnerType;

    @NotBlank(message = "编码不能为空")
    @Size(max = 32, message = "编码最长 32 位")
    private String code;

    @NotBlank(message = "名称不能为空")
    @Size(max = 128, message = "名称最长 128 位")
    private String name;

    @Size(max = 50, message = "联系人最长 50 位")
    private String contactName;

    @Size(max = 32, message = "联系电话最长 32 位")
    private String contactPhone;

    @Size(max = 255, message = "地址最长 255 位")
    private String address;

    @NotBlank(message = "状态不能为空")
    private String status;

    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}