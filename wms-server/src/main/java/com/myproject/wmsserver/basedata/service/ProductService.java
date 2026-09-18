package com.myproject.wmsserver.basedata.service;

import com.myproject.wmsserver.basedata.dto.PageQuery;
import com.myproject.wmsserver.basedata.dto.ProductSaveDTO;
import com.myproject.wmsserver.basedata.vo.ProductVO;
import com.myproject.wmsserver.common.result.PageResult;

import java.util.Collection;
import java.util.Map;

public interface ProductService {

    /** 分页查询：keyword 模糊匹配 skuCode/name/barcode，status 等值过滤 */
    PageResult<ProductVO> page(PageQuery query);

    /** 详情 */
    ProductVO get(Long id);

    /**
     * 批量按 id 查商品，返回 id -> VO。
     * 给入库 / 出库模块建单据时做 SKU 快照用：
     * 一次查完，避免在循环里逐个 get() 造成 N+1。
     */
    Map<Long, ProductVO> mapByIds(Collection<Long> ids);

    /** 新增，返回新 id */
    Long create(ProductSaveDTO dto);

    /** 修改 */
    void update(Long id, ProductSaveDTO dto);

    /** 逻辑删除 */
    void delete(Long id);
}