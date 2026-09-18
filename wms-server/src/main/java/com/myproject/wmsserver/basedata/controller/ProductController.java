package com.myproject.wmsserver.basedata.controller;

import com.myproject.wmsserver.basedata.dto.PageQuery;
import com.myproject.wmsserver.basedata.dto.ProductSaveDTO;
import com.myproject.wmsserver.basedata.service.ProductService;
import com.myproject.wmsserver.basedata.vo.ProductVO;
import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/basedata/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /** 分页查询 */
    @GetMapping
    public Result<PageResult<ProductVO>> page(@Valid PageQuery query) {
        return Result.success(productService.page(query));
    }

    /** 新增 */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ProductSaveDTO dto) {
        return Result.success(productService.create(dto));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public Result<ProductVO> get(@PathVariable Long id) {
        return Result.success(productService.get(id));
    }

    /** 修改 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProductSaveDTO dto) {
        productService.update(id, dto);
        return Result.success();
    }

    /** 删除（逻辑删除） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.success();
    }
}