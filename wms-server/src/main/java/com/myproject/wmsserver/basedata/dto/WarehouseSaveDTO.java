package com.myproject.wmsserver.basedata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 仓库新增 / 修改请求体（接口文档 6.3） */
@Data
public class WarehouseSaveDTO {

    /** 仓库编码，全局唯一，≤32 */
    @NotBlank(message = "仓库编码不能为空")
    @Size(max = 32, message = "仓库编码最长 32 位")
    private String code;

    /** 仓库名称，≤64 */
    @NotBlank(message = "仓库名称不能为空")
    @Size(max = 64, message = "仓库名称最长 64 位")
    private String name;

    /** 地址，≤255 */
    @Size(max = 255, message = "地址最长 255 位")
    private String address;

    /** ENABLED / DISABLED */
    @NotBlank(message = "状态不能为空")
    private String status;

    /** 备注，≤255 */
    @Size(max = 255, message = "备注最长 255 位")
    private String remark;
}