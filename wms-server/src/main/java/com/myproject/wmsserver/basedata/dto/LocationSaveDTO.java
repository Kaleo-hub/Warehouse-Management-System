package com.myproject.wmsserver.basedata.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 库位新增 / 修改请求体（接口文档 6.3，usedCapacity 由库存变动维护，前端不传） */
@Data
public class LocationSaveDTO {

    /** 所属仓库 ID */
    @NotNull(message = "所属仓库不能为空")
    private Long warehouseId;

    /** 所属库区 ID */
    @NotNull(message = "所属库区不能为空")
    private Long zoneId;

    /** 库位编码，同一仓库下唯一，≤64 */
    @NotBlank(message = "库位编码不能为空")
    @Size(max = 64, message = "库位编码最长 64 位")
    private String code;

    /** 库位名称，≤64 */
    @Size(max = 64, message = "库位名称最长 64 位")
    private String name;

    /** RACK / FLOOR / TEMP */
    @NotBlank(message = "库位类型不能为空")
    private String locationType;

    /** 容量上限，0 表示不限 */
    @NotNull(message = "容量上限不能为空")
    @Min(value = 0, message = "容量上限不能为负数")
    private Integer capacity;

    /** ENABLED / DISABLED */
    @NotBlank(message = "状态不能为空")
    private String status;
}