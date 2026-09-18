package com.myproject.wmsserver.basedata.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询通用参数，GET 请求的 query string 自动绑定。
 * 各模块的查询类继承它，再补充自己的过滤字段。
 */
@Data
public class PageQuery {

    /** 页码，从 1 开始 */
    @Min(value = 1, message = "page 不能小于 1")
    private int page = 1;

    /** 每页条数，可选 10/20/50/100 */
    @Min(value = 1, message = "size 不能小于 1")
    @Max(value = 100, message = "size 最大 100")
    private int size = 10;

    /** 模糊匹配关键字，各模块匹配字段不同（见接口文档 6.1） */
    private String keyword;

    /** 状态过滤：ENABLED / DISABLED，null 表示不过滤 */
    private String status;
}