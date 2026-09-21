package com.myproject.wmsserver.basedata.enums;

/**
 * 往来单位类型。数值存枚举名，不用序号（项目里统一的约定）。
 */
public enum PartnerType {

    SUPPLIER("供应商"),
    CUSTOMER("客户");

    private final String description;

    PartnerType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static boolean isValid(String name) {
        if (name == null) {
            return false;
        }
        for (PartnerType type : values()) {
            if (type.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}