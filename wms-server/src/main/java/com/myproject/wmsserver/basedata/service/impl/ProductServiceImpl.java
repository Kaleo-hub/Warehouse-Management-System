package com.myproject.wmsserver.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myproject.wmsserver.basedata.dto.PageQuery;
import com.myproject.wmsserver.basedata.dto.ProductSaveDTO;
import com.myproject.wmsserver.basedata.entity.Product;
import com.myproject.wmsserver.basedata.mapper.ProductMapper;
import com.myproject.wmsserver.basedata.service.ProductService;
import com.myproject.wmsserver.basedata.vo.ProductVO;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.CommonErrorCode;
import com.myproject.wmsserver.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品 CRUD —— 模式参考 WarehouseServiceImpl（基础数据参考实现）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;

    @Override
    public PageResult<ProductVO> page(PageQuery query) {
        Page<Product> page = productMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                Wrappers.<Product>lambdaQuery()
                        // keyword 模糊匹配 skuCode/name/barcode 三列
                        .and(StringUtils.hasText(query.getKeyword()), w -> w
                                .like(Product::getSkuCode, query.getKeyword())
                                .or().like(Product::getName, query.getKeyword())
                                .or().like(Product::getBarcode, query.getKeyword()))
                        .eq(StringUtils.hasText(query.getStatus()), Product::getStatus, query.getStatus())
                        .orderByDesc(Product::getId));

        return PageResult.of(page, page.getRecords().stream().map(this::toVO).toList());
    }

    @Override
    public ProductVO get(Long id) {
        return toVO(requireExists(id));
    }

    @Override
    public Map<Long, ProductVO> mapByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        // 用 selectList + in 而不是 selectBatchIds：
        // 后者在不同 MyBatis-Plus 版本里改过名字，in 写法最稳。
        List<Product> products = productMapper.selectList(
                Wrappers.<Product>lambdaQuery().in(Product::getId, ids));
        return products.stream().collect(Collectors.toMap(Product::getId, this::toVO));
    }

    @Override
    public Long create(ProductSaveDTO dto) {
        checkSkuCodeUnique(dto.getSkuCode(), null);
        normalizeBarcode(dto);
        checkBarcodeUnique(dto.getBarcode(), null);

        Product product = new Product();
        BeanUtils.copyProperties(dto, product);
        productMapper.insert(product);
        log.info("新增商品 id={} skuCode={}", product.getId(), dto.getSkuCode());
        return product.getId();
    }

    @Override
    public void update(Long id, ProductSaveDTO dto) {
        requireExists(id);
        checkSkuCodeUnique(dto.getSkuCode(), id);
        normalizeBarcode(dto);
        checkBarcodeUnique(dto.getBarcode(), id);

        Product product = new Product();
        BeanUtils.copyProperties(dto, product);
        product.setId(id);
        productMapper.updateById(product);
        log.info("修改商品 id={}", id);
    }

    @Override
    public void delete(Long id) {
        requireExists(id);
        // 全局 logic-delete 配置生效，deleteById 自动转成 update deleted=1
        productMapper.deleteById(id);
        log.info("删除商品 id={}", id);
    }

    /** 按 id 查，不存在抛 RESOURCE_NOT_FOUND */
    private Product requireExists(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        return product;
    }

    /**
     * skuCode 全局唯一。excludeId 为 null 表示新增；非 null 表示修改（排除自己）。
     * 建表 uk_sku_code 唯一索引兜底，并发穿透这里时由 GlobalExceptionHandler 的
     * DuplicateKeyException 处理转成 DATA_ALREADY_EXISTS。
     */
    private void checkSkuCodeUnique(String skuCode, Long excludeId) {
        boolean exists = productMapper.exists(Wrappers.<Product>lambdaQuery()
                .eq(Product::getSkuCode, skuCode)
                .ne(excludeId != null, Product::getId, excludeId));
        if (exists) {
            log.warn("SKU 编码重复 skuCode={}", skuCode);
            throw new BizException(CommonErrorCode.DATA_ALREADY_EXISTS);
        }
    }

    /**
     * 条形码为空时归一化成 NULL：uk_barcode 唯一索引下，多条 '' 会互相冲突，
     * 用 NULL 保证「未填条形码」不冲突（见建表注释）。
     */
    private void normalizeBarcode(ProductSaveDTO dto) {
        if (!StringUtils.hasText(dto.getBarcode())) {
            dto.setBarcode(null);
        }
    }

    /**
     * 条形码唯一（NULL 不查重）。建表 uk_barcode 唯一索引兜底，
     * 并发穿透这里时由 GlobalExceptionHandler 的 DuplicateKeyException 处理。
     */
    private void checkBarcodeUnique(String barcode, Long excludeId) {
        if (barcode == null) {
            return;
        }
        boolean exists = productMapper.exists(Wrappers.<Product>lambdaQuery()
                .eq(Product::getBarcode, barcode)
                .ne(excludeId != null, Product::getId, excludeId));
        if (exists) {
            log.warn("条形码重复 barcode={}", barcode);
            throw new BizException(CommonErrorCode.DATA_ALREADY_EXISTS);
        }
    }

    private ProductVO toVO(Product product) {
        ProductVO vo = new ProductVO();
        BeanUtils.copyProperties(product, vo);
        return vo;
    }
}
