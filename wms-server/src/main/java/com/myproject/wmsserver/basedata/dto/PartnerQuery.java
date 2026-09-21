package com.myproject.wmsserver.basedata.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 往来单位查询条件 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PartnerQuery extends PageQuery {

    /** SUPPLIER / CUSTOMER，null 表示两类都要 */
    private String partnerType;
}