package com.myproject.wmsserver.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myproject.wmsserver.common.context.UserContext;
import com.myproject.wmsserver.common.exception.AuthErrorCode;
import com.myproject.wmsserver.common.exception.BizException;
import com.myproject.wmsserver.common.exception.CommonErrorCode;
import com.myproject.wmsserver.common.result.PageResult;
import com.myproject.wmsserver.system.dto.PasswordDTO;
import com.myproject.wmsserver.system.dto.UserQuery;
import com.myproject.wmsserver.system.dto.UserSaveDTO;
import com.myproject.wmsserver.system.entity.SysUser;
import com.myproject.wmsserver.system.enums.UserRole;
import com.myproject.wmsserver.system.mapper.SysUserMapper;
import com.myproject.wmsserver.system.service.SysUserService;
import com.myproject.wmsserver.system.vo.SysUserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<SysUserVO> page(UserQuery query) {
        Page<SysUser> page = sysUserMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                Wrappers.<SysUser>lambdaQuery()
                        .and(StringUtils.hasText(query.getKeyword()), w -> w
                                .like(SysUser::getUsername, query.getKeyword())
                                .or().like(SysUser::getRealName, query.getKeyword()))
                        .eq(StringUtils.hasText(query.getRole()), SysUser::getRole, query.getRole())
                        .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                        .orderByDesc(SysUser::getId));

        List<SysUserVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(page, records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserSaveDTO dto) {
        checkRoleValid(dto.getRole());
        if (!StringUtils.hasText(dto.getPassword())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }

        Long exists = sysUserMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException(CommonErrorCode.DATA_ALREADY_EXISTS);
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setRealName(dto.getRealName());
        user.setRole(dto.getRole());
        user.setStatus(dto.getStatus() == null ? STATUS_ENABLED : dto.getStatus());
        // 密码永远只存 BCrypt 哈希，永远不存明文，也不回传
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        sysUserMapper.insert(user);

        log.info("新建用户 id={} username={} role={}", user.getId(), user.getUsername(), user.getRole());
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserSaveDTO dto) {
        SysUser user = requireUser(id);
        checkRoleValid(dto.getRole());

        int newStatus = dto.getStatus() == null ? user.getStatus() : dto.getStatus();
        boolean wasAdmin = UserRole.ADMIN.name().equals(user.getRole());
        boolean stillAdmin = UserRole.ADMIN.name().equals(dto.getRole()) && newStatus == STATUS_ENABLED;

        // 规则 1：不能把自己禁用（否则下一次就登不进来了）
        if (id.equals(UserContext.getUserId()) && newStatus == STATUS_DISABLED) {
            log.warn("拒绝禁用自己 userId={}", id);
            throw new BizException(AuthErrorCode.SELF_PROTECTED);
        }
        // 规则 2：不能把最后一个启用的管理员降级 / 禁用
        if (wasAdmin && !stillAdmin) {
            ensureAnotherEnabledAdmin(id);
        }

        user.setRealName(dto.getRealName());
        user.setRole(dto.getRole());
        user.setStatus(newStatus);
        sysUserMapper.updateById(user);

        log.info("修改用户 id={} role={} status={}", id, user.getRole(), user.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, PasswordDTO dto) {
        SysUser user = requireUser(id);
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        sysUserMapper.updateById(user);
        log.info("重置用户密码 id={} username={} 操作人={}",
                id, user.getUsername(), UserContext.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser user = requireUser(id);

        if (id.equals(UserContext.getUserId())) {
            throw new BizException(AuthErrorCode.SELF_PROTECTED);
        }
        if (UserRole.ADMIN.name().equals(user.getRole()) && user.getStatus() == STATUS_ENABLED) {
            ensureAnotherEnabledAdmin(id);
        }

        // 全局逻辑删除配置生效，deleteById 自动转成 update deleted = 1
        sysUserMapper.deleteById(id);
        log.info("删除用户 id={} username={}", id, user.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeOwnPassword(PasswordDTO dto) {
        SysUser user = requireUser(UserContext.getUserId());

        if (!StringUtils.hasText(dto.getOldPassword())
                || !passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            log.warn("旧密码错误 userId={}", user.getId());
            throw new BizException(AuthErrorCode.BAD_CREDENTIALS);
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        sysUserMapper.updateById(user);
        log.info("用户自己修改密码 userId={}", user.getId());
    }

    /* ==================== 小工具 ==================== */

    private SysUser requireUser(Long id) {
        SysUser user = id == null ? null : sysUserMapper.selectById(id);
        if (user == null) {
            throw new BizException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
        return user;
    }

    private void checkRoleValid(String role) {
        if (!UserRole.isValid(role)) {
            log.warn("非法角色 role={}", role);
            throw new BizException(CommonErrorCode.PARAM_INVALID);
        }
    }

    /**
     * 确认除 excludeId 之外还有别的「启用的管理员」。
     * 没有就拒绝 —— 这是防止把系统锁死的关键一条。
     */
    private void ensureAnotherEnabledAdmin(Long excludeId) {
        Long others = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRole, UserRole.ADMIN.name())
                .eq(SysUser::getStatus, STATUS_ENABLED)
                .ne(SysUser::getId, excludeId));
        if (others == null || others == 0) {
            log.warn("拒绝操作：系统里只剩这一个启用的管理员 excludeId={}", excludeId);
            throw new BizException(AuthErrorCode.LAST_ADMIN_PROTECTED);
        }
    }

    private SysUserVO toVO(SysUser user) {
        SysUserVO vo = new SysUserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}