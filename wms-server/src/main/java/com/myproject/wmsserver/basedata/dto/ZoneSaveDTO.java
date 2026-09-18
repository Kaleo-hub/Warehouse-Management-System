package com.myproject.wmsserver.basedata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 库区新增 / 修改请求体（接口文档 6.3） */
@Data
public class ZoneSaveDTO {

    /** 所属仓库 ID */
    @NotNull(message = "所属仓库不能为空")
    private Long warehouseId;

    /** 库区编码，同一仓库下唯一，≤32 */
    @NotBlank(message = "库区编码不能为空")
    @Size(max = 32, message = "库区编码最长 32 位")
    private String code;

    /** 库区名称，≤64 */
    @NotBlank(message = "库区名称不能为空")
    @Size(max = 64, message = "库区名称最长 64 位")
    private String name;

    /** STORAGE / PICKING / RECEIVING / SHIPPING / SORTING */
    @NotBlank(message = "库区类型不能为空")
    private String zoneType;

    /** 排序号 */
    @NotNull(message = "排序号不能为空")
    private Integer sortNo;

    /** ENABLED / DISABLED */
    @NotBlank(message = "状态不能为空")
    private String status;
}