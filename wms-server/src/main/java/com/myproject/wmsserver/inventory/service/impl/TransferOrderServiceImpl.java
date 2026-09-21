package com.myproject.wmsserver.inventory.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myproject.wmsserver.basedata.service.LocationService;
import com.myproject.wmsserver.basedata.service.ProductService;
import com.myproject.wmsserver.basedata.vo.LocationVO;
import com.myproject.wmsserver.basedata.vo.ProductVO;
import com.myproject.wmsserver.common.context.UserContext;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.CommonErrorCode;
import com.myproject.wmsserver.common.exception.InventoryErrorCode;
import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.common.sequence.SequenceService;
import com.myproject.wmsserver.inventory.dto.TransferDTO;
import com.myproject.wmsserver.inventory.dto.TransferOrderCreateDTO;
import com.myproject.wmsserver.inventory.dto.TransferOrderItemDTO;
import com.myproject.wmsserver.inventory.dto.TransferOrderQuery;
import com.myproject.wmsserver.inventory.entity.Inventory;
import com.myproject.wmsserver.inventory.entity.TransferOrder;
import com.myproject.wmsserver.inventory.entity.TransferOrderItem;
import com.myproject.wmsserver.inventory.enums.TransferOrderStatus;
import com.myproject.wmsserver.inventory.mapper.InventoryMapper;
import com.myproject.wmsserver.inventory.mapper.TransferOrderItemMapper;
import com.myproject.wmsserver.inventory.mapper.TransferOrderMapper;
import com.myproject.wmsserver.inventory.service.InventoryAdjustService;
import com.myproject.wmsserver.inventory.service.TransferOrderService;
import com.myproject.wmsserver.inventory.vo.TransferOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferOrderServiceImpl implements TransferOrderService {

    private static final String BIZ_TYPE = "TRANSFER_ORDER";
    private static final String ORDER_NO_PREFIX = "TR";
    private static final String SOURCE_TYPE = "TRANSFER_ORDER";

    private final TransferOrderMapper orderMapper;
    private final TransferOrderItemMapper itemMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryAdjustService inventoryAdjustService;
    private final LocationService locationService;
    private final ProductService productService;
    private final SequenceService sequenceService;

    @Override
    public PageResult<TransferOrderVO> page(TransferOrderQuery query) {
        Page<TransferOrderVO> page = new Page<>(query.getPage(), query.getSize());
        orderMapper.selectOrderPage(page, query);
        return PageResult.of(page, page.getRecords());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TransferOrderCreateDTO dto) {
        if (dto.getFromLocationId().equals(dto.getToLocationId())) {
            throw new BizException(InventoryErrorCode.TRANSFER_SAME_LOCATION);
        }
        LocationVO from = locationService.get(dto.getFromLocationId());
        LocationVO to = locationService.get(dto.getToLocationId());
        if (!"ENABLED".equals(from.getStatus()) || !"ENABLED".equals(to.getStatus())) {
            throw new BizException(InventoryErrorCode.LOCATION_DISABLED);
        }
        if (!from.getWarehouseId().equals(to.getWarehouseId())) {
            throw new BizException(InventoryErrorCode.TRANSFER_CROSS_WAREHOUSE);
        }

        TransferOrder order = new TransferOrder();
        order.setOrderNo(sequenceService.nextNo(BIZ_TYPE, ORDER_NO_PREFIX));
        order.setWarehouseId(from.getWarehouseId());
        order.setFromLocationId(from.getId());
        order.setFromLocationCode(from.getCode());
        order.setToLocationId(to.getId());
        order.setToLocationCode(to.getCode());
        order.setStatus(TransferOrderStatus.CREATED.name());
        order.setTotalQty(dto.getItems().stream().mapToInt(TransferOrderItemDTO::getQty).sum());
        order.setRemark(nvl(dto.getRemark()));
        order.setCreateBy(UserContext.getUserId());
        order.setUpdateBy(UserContext.getUserId());
        orderMapper.insert(order);

        Set<String> keys = new HashSet<>();
        for (TransferOrderItemDTO line : dto.getItems()) {
            String batchNo = normalizeBatchNo(line.getBatchNo());
            if (!keys.add(line.getSkuId() + "::" + batchNo)) {
                throw new BizException(CommonErrorCode.PARAM_INVALID);
            }
            ProductVO product = productService.get(line.getSkuId());
            Inventory source = inventoryMapper.selectByBusinessKey(
                    from.getId(), line.getSkuId(), batchNo);
            if (source == null) {
                throw new BizException(InventoryErrorCode.STOCK_NOT_FOUND);
            }
            if (nz(source.getAvailableQty()) < line.getQty()) {
                throw new BizException(InventoryErrorCode.AVAILABLE_NOT_ENOUGH);
            }

            TransferOrderItem item = new TransferOrderItem();
            item.setOrderId(order.getId());
            item.setSkuId(line.getSkuId());
            item.setSkuCode(product.getSkuCode());
            item.setSkuName(product.getName());
            item.setBatchNo(batchNo);
            item.setQty(line.getQty());
            item.setRemark(nvl(line.getRemark()));
            item.setCreateBy(UserContext.getUserId());
            item.setUpdateBy(UserContext.getUserId());
            itemMapper.insert(item);
        }

        log.info("移库单创建 orderNo={} from={} to={} totalQty={} items={}",
                order.getOrderNo(), order.getFromLocationCode(), order.getToLocationCode(),
                order.getTotalQty(), dto.getItems().size());
        return order.getId();
    }

    @Override
    public TransferOrderVO get(Long id) {
        TransferOrderVO vo = orderMapper.selectOrderDetail(id);
        if (vo == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        vo.setItems(itemMapper.selectByOrderId(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id) {
        TransferOrder order = requireCreatedOrderForUpdate(id);
        List<TransferOrderItem> items = itemMapper.selectList(Wrappers.<TransferOrderItem>lambdaQuery()
                .eq(TransferOrderItem::getOrderId, id)
                .orderByAsc(TransferOrderItem::getId));

        for (TransferOrderItem item : items) {
            TransferDTO dto = new TransferDTO();
            dto.setFromLocationId(order.getFromLocationId());
            dto.setToLocationId(order.getToLocationId());
            dto.setSkuId(item.getSkuId());
            dto.setBatchNo(item.getBatchNo());
            dto.setQty(item.getQty());
            dto.setRemark("移库单 " + order.getOrderNo() + "：" + nvl(item.getRemark()));
            inventoryAdjustService.transfer(dto, SOURCE_TYPE, order.getId(), order.getOrderNo());
        }

        int affected = orderMapper.markCompleted(id, UserContext.getUserId());
        if (affected == 0) {
            throw new BizException(CommonErrorCode.OPERATION_FORBIDDEN);
        }
        log.info("移库单完成 orderNo={} items={} totalQty={}",
                order.getOrderNo(), items.size(), order.getTotalQty());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        int affected = orderMapper.cancel(id, UserContext.getUserId());
        if (affected == 0) {
            throw new BizException(CommonErrorCode.OPERATION_FORBIDDEN);
        }
    }

    private TransferOrder requireCreatedOrderForUpdate(Long id) {
        TransferOrder order = orderMapper.selectForUpdate(id);
        if (order == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!TransferOrderStatus.CREATED.name().equals(order.getStatus())) {
            throw new BizException(CommonErrorCode.OPERATION_FORBIDDEN);
        }
        return order;
    }

    private String normalizeBatchNo(String batchNo) {
        return batchNo == null ? "" : batchNo.trim();
    }

    private String nvl(String value) {
        return value == null ? "" : value.trim();
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }
}