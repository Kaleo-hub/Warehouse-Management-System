package com.myproject.wmsserver.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 分页响应体。字段名和前端约定一致：records / total / page / size。
 */
@Data
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long page;
    private long size;

    /** 从 MyBatis-Plus 的 IPage 转出。records 需要调用方自己转换（比如 Entity → VO） */
    public static <T> PageResult<T> of(IPage<?> page, List<T> records) {
        PageResult<T> r = new PageResult<>();
        r.setRecords(records);
        r.setTotal(page.getTotal());
        r.setPage(page.getCurrent());
        r.setSize(page.getSize());
        return r;
    }
}