package com.myproject.wmsserver.system.enums;

/**
 * 用户角色。全项目的权限矩阵只有这一处定义，别在别处再写一份。
 *
 * ========== 三个角色怎么分工 ==========
 *
 *   ADMIN     系统管理员：一切权限，额外拥有「用户管理」（建人 / 停用 / 改角色 / 重置密码）
 *
 *   MANAGER   仓库主管：主数据维护（仓库/库区/库位/商品）+ 库存调整（冻结/解冻/移库/盘点/对账）
 *             依据 docs/01 第 2 节：主管负责「单据审核、库存调整、盘点」
 *
 *   OPERATOR  作业员：现场作业（收货、上架、拣货）+ 查询
 *             不能碰主数据，也不能做库存调整 —— 那是会改账的动作
 *
 * ========== 为什么只用三个角色，不做细粒度权限表 ==========
 * RBAC 做到「角色 → 权限点 → 接口」当然更灵活，但要配套权限管理界面、
 * 缓存、动态菜单，收益和复杂度不成正比。
 * 学习项目里三个角色 + 注解足以讲清楚「认证和授权是两件事」；
 * 真需要细粒度时，升级路径是清晰的：把 @PreAuthorize 里的角色换成权限点即可。
 *
 * ========== 注解里的角色名为什么还是字符串 ==========
 * @PreAuthorize 的表达式必须是编译期常量，塞不进枚举。
 * 所以这里用枚举做「合法性校验」（用户传的 role 要能转成这个枚举），
 * 注解里写字符串，两边靠这道枚举卡住拼写错误。
 */
public enum UserRole {

    ADMIN("系统管理员"),
    MANAGER("仓库主管"),
    OPERATOR("作业员");

    private final String description;

    UserRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /** 角色名是否合法。用户在 DTO 里传了乱七八糟的角色时用它挡下来 */
    public static boolean isValid(String name) {
        if (name == null) {
            return false;
        }
        for (UserRole role : values()) {
            if (role.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}