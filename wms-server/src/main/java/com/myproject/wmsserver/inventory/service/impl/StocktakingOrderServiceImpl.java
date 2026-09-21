package com.myproject.wmsserver.inventory.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myproject.wmsserver.basedata.service.LocationService;
import com.myproject.wmsserver.basedata.vo.LocationVO;
import com.myproject.wmsserver.common.context.UserContext;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.CommonErrorCode;
import com.myproject.wmsserver.common.exception.InventoryErrorCode;
import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.common.sequence.SequenceService;
import com.myproject.wmsserver.inventory.dto.StocktakingCountDTO;
import com.myproject.wmsserver.inventory.dto.StocktakingCountLineDTO;
import com.myproject.wmsserver.inventory.dto.StocktakingDTO;
import com.myproject.wmsserver.inventory.dto.StocktakingOrderCreateDTO;
import com.myproject.wmsserver.inventory.dto.StocktakingOrderQuery;
import com.myproject.wmsserver.inventory.entity.StocktakingOrder;
import com.myproject.wmsserver.inventory.entity.StocktakingOrderItem;
import com.myproject.wmsserver.inventory.enums.StocktakingOrderStatus;
import com.myproject.wmsserver.inventory.mapper.InventoryMapper;
import com.myproject.wmsserver.inventory.mapper.StocktakingOrderItemMapper;
import com.myproject.wmsserver.inventory.mapper.StocktakingOrderMapper;
import com.myproject.wmsserver.inventory.service.InventoryAdjustService;
import com.myproject.wmsserver.inventory.service.StocktakingOrderService;
import com.myproject.wmsserver.inventory.vo.StocktakingCandidateVO;
import com.myproject.wmsserver.inventory.vo.StocktakingOrderVO;
import com.myproject.wmsserver.inventory.vo.StocktakingResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StocktakingOrderServiceImpl implements StocktakingOrderService {

    private static final String BIZ_TYPE = "STOCKTAKING_ORDER";
    private static final String ORDER_NO_PREFIX = "ST";

    private final StocktakingOrderMapper orderMapper;
    private final StocktakingOrderItemMapper itemMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryAdjustService inventoryAdjustService;
    private final LocationService locationService;
    private final SequenceService sequenceService;

    @Override
    public PageResult<StocktakingOrderVO> page(StocktakingOrderQuery query) {
        Page<StocktakingOrderVO> page = new Page<>(query.getPage(), query.getSize());
        orderMapper.selectOrderPage(page, query);
        return PageResult.of(page, page.getRecords());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(StocktakingOrderCreateDTO dto) {
        LocationVO location = locationService.get(dto.getLocationId());
        if (!"ENABLED".equals(location.getStatus())) {
            throw new BizException(InventoryErrorCode.LOCATION_DISABLED);
        }

        List<StocktakingCandidateVO> candidates =
                inventoryMapper.selectStocktakingCandidates(dto.getLocationId());
        if (candidates.isEmpty()) {
            throw new BizException(InventoryErrorCode.STOCK_NOT_FOUND);
        }

        StocktakingOrder order = new StocktakingOrder();
        order.setOrderNo(sequenceService.nextNo(BIZ_TYPE, ORDER_NO_PREFIX));
        order.setWarehouseId(location.getWarehouseId());
        order.setLocationId(location.getId());
        order.setLocationCode(location.getCode());
        order.setLocationName(location.getName());
        order.setStatus(StocktakingOrderStatus.COUNTING.name());
        order.setTotalItems(candidates.size());
        order.setCountedItems(0);
        order.setDiffItems(0);
        order.setGainQty(0);
        order.setLossQty(0);
        order.setRemark(nvl(dto.getRemark()));
        order.setCreateBy(UserContext.getUserId());
        order.setUpdateBy(UserContext.getUserId());
        orderMapper.insert(order);

        for (StocktakingCandidateVO candidate : candidates) {
            StocktakingOrderItem item = new StocktakingOrderItem();
            item.setOrderId(order.getId());
            item.setInventoryId(candidate.getInventoryId());
            item.setSkuId(candidate.getSkuId());
            item.setSkuCode(candidate.getSkuCode());
            item.setSkuName(candidate.getSkuName());
            item.setBatchNo(candidate.getBatchNo());
            item.setSystemQty(candidate.getSystemQty());
            item.setLockedQty(candidate.getLockedQty());
            item.setFrozenQty(candidate.getFrozenQty());
            item.setRemark("");
            item.setCreateBy(UserContext.getUserId());
            item.setUpdateBy(UserContext.getUserId());
            itemMapper.insert(item);
        }

        log.info("盘点单创建 orderNo={} location={} items={}",
                order.getOrderNo(), order.getLocationCode(), candidates.size());
        return order.getId();
    }

    @Override
    public StocktakingOrderVO get(Long id) {
        StocktakingOrderVO vo = orderMapper.selectOrderDetail(id);
        if (vo == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        vo.setItems(itemMapper.selectByOrderId(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveCounts(Long id, StocktakingCountDTO dto) {
        StocktakingOrder order = requireCountingOrderForUpdate(id);
        List<StocktakingOrderItem> items = listItems(id);
        Map<Long, StocktakingOrderItem> byId = new HashMap<>();
        items.forEach(item -> byId.put(item.getId(), item));

        for (StocktakingCountLineDTO line : dto.getItems()) {
            StocktakingOrderItem item = byId.get(line.getId());
            if (item == null) {
                throw new BizException(CommonErrorCode.PARAM_INVALID);
            }
            if (line.getActualQty() < nz(item.getLockedQty()) + nz(item.getFrozenQty())) {
                throw new BizException(InventoryErrorCode.AVAILABLE_NOT_ENOUGH);
            }
            item.setActualQty(line.getActualQty());
            item.setDiffQty(line.getActualQty() - nz(item.getSystemQty()));
            item.setRemark(nvl(line.getRemark()));
            item.setUpdateBy(UserContext.getUserId());
            itemMapper.updateById(item);
        }

        Summary summary = summarize(items);
        int affected = orderMapper.updateCountSummary(
                order.getId(), summary.countedItems, summary.diffItems,
                summary.gainQty, summary.lossQty, UserContext.getUserId());
        if (affected == 0) {
            throw new BizException(CommonErrorCode.OPERATION_FORBIDDEN);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void post(Long id) {
        StocktakingOrder order = requireCountingOrderForUpdate(id);
        List<StocktakingOrderItem> items = listItems(id);
        items.sort(Comparator.comparing(StocktakingOrderItem::getInventoryId));

        if (items.stream().anyMatch(item -> item.getActualQty() == null)) {
            throw new BizException(InventoryErrorCode.STOCKTAKING_ITEM_INCOMPLETE);
        }

        for (StocktakingOrderItem item : items) {
            StocktakingDTO dto = new StocktakingDTO();
            dto.setLocationId(order.getLocationId());
            dto.setSkuId(item.getSkuId());
            dto.setBatchNo(item.getBatchNo());
            dto.setActualQty(item.getActualQty());
            dto.setRemark("盘点单 " + order.getOrderNo() + "：" + nvl(item.getRemark()));

            StocktakingResultVO result = inventoryAdjustService.stocktakeForOrder(
                    dto, order.getId(), order.getOrderNo(), nz(item.getSystemQty()));
            item.setDiffQty(result.getDiffQty());
            if (result.isChanged()) {
                item.setUpdateBy(UserContext.getUserId());
                itemMapper.updateById(item);
            }
        }

        Summary summary = summarize(items);
        int affected = orderMapper.markCompleted(
                order.getId(), summary.countedItems, summary.diffItems,
                summary.gainQty, summary.lossQty, UserContext.getUserId());
        if (affected == 0) {
            throw new BizException(CommonErrorCode.OPERATION_FORBIDDEN);
        }
        log.info("盘点单过账 orderNo={} items={} diff={} gain={} loss={}",
                order.getOrderNo(), summary.countedItems, summary.diffItems,
                summary.gainQty, summary.lossQty);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        int affected = orderMapper.cancel(id, UserContext.getUserId());
        if (affected == 0) {
            throw new BizException(CommonErrorCode.OPERATION_FORBIDDEN);
        }
    }

    private StocktakingOrder requireCountingOrderForUpdate(Long id) {
        StocktakingOrder order = orderMapper.selectForUpdate(id);
        if (order == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!StocktakingOrderStatus.COUNTING.name().equals(order.getStatus())) {
            throw new BizException(CommonErrorCode.OPERATION_FORBIDDEN);
        }
        return order;
    }

    private List<StocktakingOrderItem> listItems(Long orderId) {
        return itemMapper.selectList(Wrappers.<StocktakingOrderItem>lambdaQuery()
                .eq(StocktakingOrderItem::getOrderId, orderId)
                .orderByAsc(StocktakingOrderItem::getId));
    }

    private Summary summarize(List<StocktakingOrderItem> items) {
        Summary summary = new Summary();
        for (StocktakingOrderItem item : items) {
            if (item.getActualQty() == null) {
                continue;
            }
            summary.countedItems++;
            int diff = item.getActualQty() - nz(item.getSystemQty());
            if (diff > 0) {
                summary.diffItems++;
                summary.gainQty += diff;
            } else if (diff < 0) {
                summary.diffItems++;
                summary.lossQty += -diff;
            }
        }
        return summary;
    }

    private static class Summary {
        private int countedItems;
        private int diffItems;
        private int gainQty;
        private int lossQty;
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private String nvl(String value) {
        return value == null ? "" : value.trim();
    }
}