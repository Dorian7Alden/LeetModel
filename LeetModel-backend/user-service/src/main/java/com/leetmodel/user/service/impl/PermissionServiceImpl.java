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
     * 查询系统中全部权限定义列表。
     *
     * @return 权限视图对象列表
     */
    @Override
    public List<PermissionVO> listPermissions() {
        return permissionMapper.selectList(null).stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 根据权限 ID 查询权限详情。
     *
     * @param permissionId 目标权限 ID，不能为 null
     * @return 权限视图对象
     * @throws BusinessException 若权限不存在
     */
    @Override
    public PermissionVO getPermissionById(Long permissionId) {
        Permission permission = getExistingPermission(permissionId);
        return toVO(permission);
    }

    /**
     * 创建新的系统权限定义。
     *
     * @param request 包含权限属性的创建请求对象，不能为 null
     * @return 创建成功后的权限视图对象
     * @throws BusinessException 若权限编码重复
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

        log.info("创建权限完成: id={}", permission.getId());
        return toVO(permission);
    }

    /**
     * 更新指定权限的属性定义。
     *
     * @param permissionId 目标权限 ID，不能为 null
     * @param request      包含待修改信息的请求对象，不能为 null
     * @return 更新后的权限视图对象
     * @throws BusinessException 若权限不存在或修改后的编码重复
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

        log.info("更新权限完成: id={}", permissionId);
        return toVO(permission);
    }

    /**
     * 删除指定权限（若权限仍被角色引用则禁止删除）。
     *
     * @param permissionId 目标权限 ID，不能为 null
     * @throws BusinessException 若权限不存在或仍被角色使用
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
        log.info("删除权限完成: id={}", permissionId);
    }

    // ==================== 私有方法 ====================

    /**
     * 校验并获取已存在的权限实体。
     *
     * @param permissionId 目标权限 ID，不能为 null
     * @return 存在的权限实体
     * @throws BusinessException 若权限不存在
     */
    private Permission getExistingPermission(Long permissionId) {
        Permission permission = permissionMapper.selectById(permissionId);
        BusinessException.throwIf(permission == null, UserErrorCode.PERMISSION_NOT_FOUND);
        return permission;
    }

    /**
     * 检查权限编码是否全局唯一。
     *
     * @param code 待检查的权限编码，不能为 null
     * @throws BusinessException 若编码已被占用
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
     * 将请求对象的数据映射填充至实体字段。
     *
     * @param permission 目标权限实体
     * @param request    来源请求对象
     */
    private void updatePermissionFields(Permission permission, PermissionRequest request) {
        permission.setCode(request.getCode());
        permission.setName(request.getName());
        permission.setDescription(request.getDescription());
    }

    /**
     * 将权限实体转换为视图对象。
     *
     * @param permission 权限实体
     * @return 权限视图对象
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
