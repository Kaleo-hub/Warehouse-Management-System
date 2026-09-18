package com.myproject.wmsserver.inbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 收货登记请求体。
 *
 * 【设计要点】整单一次提交，不是一个明细一个请求。
 * 前端点一次「提交收货」发一个请求，body 里带多行；
 * 后端任一校验不过就整单回滚 —— 这就是 docs/01 里说的「整单原子」。
 */
@Data
public class InboundReceiveDTO {

    @Valid
    @NotEmpty(message = "收货明细不能为空")
    private List<ReceiveItemDTO> items;
}