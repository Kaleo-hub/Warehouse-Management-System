package com.myproject.wmsserver.system.service;

import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.system.dto.PasswordDTO;
import com.myproject.wmsserver.system.dto.UserQuery;
import com.myproject.wmsserver.system.dto.UserSaveDTO;
import com.myproject.wmsserver.system.vo.SysUserVO;

/**
 * 用户管理（只有 ADMIN 能用，权限卡在 Controller 上）。
 *
 * 这里顺手把两条「防止把自己锁在门外」的业务规则也实现了：
 *   1. 不能禁用 / 删除自己
 *   2. 不能把最后一个启用的管理员降级或禁用
 * 这类规则看起来啰嗦，但少了它，一次误操作就没人能进后台了。
 */
public interface SysUserService {

    PageResult<SysUserVO> page(UserQuery query);

    /** 新建用户，返回新 id。password 必填 */
    Long create(UserSaveDTO dto);

    /** 修改姓名 / 角色 / 状态。username 不可改 */
    void update(Long id, UserSaveDTO dto);

    /** 管理员重置别人（或自己）的密码，不需要旧密码 */
    void resetPassword(Long id, PasswordDTO dto);

    /** 逻辑删除用户 */
    void delete(Long id);

    /** 本人改密码：必须校验旧密码 */
    void changeOwnPassword(PasswordDTO dto);
}