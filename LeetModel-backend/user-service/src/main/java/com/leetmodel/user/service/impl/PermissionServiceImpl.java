package com.leetmodel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.api.dto.PermissionRequest;
import com.leetmodel.common.api.vo.PermissionVO;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.user.entity.Permission;
import com.leetmodel.user.entity.RolePermission;
import com.leetmodel.user.enums.UserErrorCode;
import com.leetmodel.user.mapper.PermissionMapper;
import com.leetmodel.user.mapper.RolePermissionMapper;
import com.leetmodel.user.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限管理服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;

    /**
     * 获取权限列表。
     * @return 权限列表
     */
    @Override
    public List<PermissionVO> listPermissions() {
        return permissionMapper.selectList(null).stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 获取权限详情。
     * @param permissionId 权限 ID
     * @return 权限详情
     */
    @Override
    public PermissionVO getPermissionById(Long permissionId) {
        Permission permission = getExistingPermission(permissionId);
        return toVO(permission);
    }

    /**
     * 创建权限。
     * @param request 权限信息
     * @return 创建后的权限
     */
    @Override
    @Transactional
    public PermissionVO createPermission(PermissionRequest request) {
        // 校验权限编码唯一
        ensurePermissionCodeUnique(request.getCode());

        // 创建权限
        Permission permission = new Permission();
        updatePermissionFields(permission, request);
        LocalDateTime now = LocalDateTime.now();
        permission.setCreateTime(now);
        permission.setUpdateTime(now);
        permissionMapper.insert(permission);

        log.info("创建权限: {} ({})", permission.getCode(), permission.getId());
        return toVO(permission);
    }

    /**
     * 更新权限。
     * @param permissionId 权限 ID
     * @param request 权限信息
     * @return 更新后的权限
     */
    @Override
    @Transactional
    public PermissionVO updatePermission(Long permissionId, PermissionRequest request) {
        // 获取权限
        Permission permission = getExistingPermission(permissionId);

        // 编码变更时校验唯一
        if (!permission.getCode().equals(request.getCode())) {
            ensurePermissionCodeUnique(request.getCode());
        }

        // 更新权限
        updatePermissionFields(permission, request);
        permission.setUpdateTime(LocalDateTime.now());
        permissionMapper.updateById(permission);

        log.info("更新权限: {} ({})", permission.getCode(), permissionId);
        return toVO(permission);
    }

    /**
     * 删除未被角色使用的权限。
     * @param permissionId 权限 ID
     */
    @Override
    @Transactional
    public void deletePermission(Long permissionId) {
        // 获取权限
        Permission permission = getExistingPermission(permissionId);

        // 防止删除仍被角色使用的权限
        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermission::getPermissionId, permissionId);
        BusinessException.throwIf(
                rolePermissionMapper.selectCount(wrapper) > 0,
                UserErrorCode.PERMISSION_IN_USE
        );

        // 删除权限
        permissionMapper.deleteById(permissionId);
        log.info("删除权限: {} ({})", permission.getCode(), permissionId);
    }

    // ==================== 私有方法 ====================

    /**
     * 获取存在的权限。
     * @param permissionId 权限 ID
     * @return 权限实体
     */
    private Permission getExistingPermission(Long permissionId) {
        Permission permission = permissionMapper.selectById(permissionId);
        BusinessException.throwIf(permission == null, UserErrorCode.PERMISSION_NOT_FOUND);
        return permission;
    }

    /**
     * 校验权限编码唯一。
     * @param code 权限编码
     */
    private void ensurePermissionCodeUnique(String code) {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Permission::getCode, code);
        BusinessException.throwIf(
                permissionMapper.selectCount(wrapper) > 0,
                UserErrorCode.PERMISSION_CODE_DUPLICATE
        );
    }

    /**
     * 使用请求数据更新权限字段。
     * @param permission 权限实体
     * @param request 权限请求
     */
    private void updatePermissionFields(Permission permission, PermissionRequest request) {
        permission.setCode(request.getCode());
        permission.setName(request.getName());
        permission.setDescription(request.getDescription());
    }

    /**
     * 将权限实体转换为 VO。
     * @param permission 权限实体
     * @return 权限 VO
     */
    private PermissionVO toVO(Permission permission) {
        return PermissionVO.builder()
                .id(permission.getId())
                .code(permission.getCode())
                .name(permission.getName())
                .description(permission.getDescription())
                .createTime(permission.getCreateTime())
                .updateTime(permission.getUpdateTime())
                .build();
    }
}
