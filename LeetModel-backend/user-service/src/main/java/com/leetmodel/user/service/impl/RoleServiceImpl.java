package com.leetmodel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.api.dto.RoleRequest;
import com.leetmodel.common.api.dto.UserRoleDTO;
import com.leetmodel.common.api.vo.PermissionVO;
import com.leetmodel.common.api.vo.RoleVO;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.user.entity.Permission;
import com.leetmodel.user.entity.Role;
import com.leetmodel.user.entity.RolePermission;
import com.leetmodel.user.entity.UserRole;
import com.leetmodel.user.audit.UserAuditEventProducer;
import com.leetmodel.user.enums.UserErrorCode;
import com.leetmodel.user.mapper.PermissionMapper;
import com.leetmodel.user.mapper.RoleMapper;
import com.leetmodel.user.mapper.RolePermissionMapper;
import com.leetmodel.user.mapper.UserRoleMapper;
import com.leetmodel.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色权限服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private static final Set<String> SYSTEM_ROLE_CODES = Set.of("admin", "vip", "user");

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final UserAuditEventProducer audit;

    /**
     * 查询用户的角色编码与权限编码集合。
     *
     * @param userId 目标用户 ID，不能为 null
     * @return 角色与权限 DTO 对象
     */
    @Override
    public UserRoleDTO getUserRoles(Long userId) {
        // 查询用户角色关联
        LambdaQueryWrapper<UserRole> urWrapper = new LambdaQueryWrapper<>();
        urWrapper.eq(UserRole::getUserId, userId);
        List<UserRole> userRoles = userRoleMapper.selectList(urWrapper);

        // 没有角色时返回空数据
        if (userRoles.isEmpty()) {
            return new UserRoleDTO(userId, Collections.emptyList(), Collections.emptyList());
        }

        // 查询角色编码
        List<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .toList();
        List<String> roles = roleMapper.selectBatchIds(roleIds).stream()
                .map(Role::getCode)
                .toList();

        // 查询权限 ID 并去重
        LambdaQueryWrapper<RolePermission> rpWrapper = new LambdaQueryWrapper<>();
        rpWrapper.in(RolePermission::getRoleId, roleIds);
        Set<Long> permissionIds = rolePermissionMapper.selectList(rpWrapper).stream()
                .map(RolePermission::getPermissionId)
                .collect(Collectors.toSet());

        // 查询权限编码
        List<String> permissions = Collections.emptyList();
        if (!permissionIds.isEmpty()) {
            permissions = permissionMapper.selectBatchIds(permissionIds).stream()
                    .map(Permission::getCode)
                    .toList();
        }

        return new UserRoleDTO(userId, roles, permissions);
    }

    // ==================== 角色 CRUD ====================

    /**
     * 获取系统中所有角色列表。
     *
     * @return 角色视图对象列表
     */
    @Override
    public List<RoleVO> listRoles() {
        return roleMapper.selectList(null).stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 根据角色 ID 查询角色详情。
     *
     * @param roleId 目标角色 ID，不能为 null
     * @return 角色视图对象
     * @throws BusinessException 若角色不存在
     */
    @Override
    public RoleVO getRoleById(Long roleId) {
        Role role = roleMapper.selectById(roleId);
        BusinessException.throwIf(role == null, UserErrorCode.ROLE_NOT_FOUND);
        return toVO(role);
    }

    /**
     * 创建新的系统角色。
     *
     * @param request 角色创建请求对象，不能为 null
     * @return 创建成功后的角色视图对象
     * @throws BusinessException 若角色编码重复
     */
    @Override
    @Transactional
    public RoleVO createRole(RoleRequest request) {
        // 校验角色编码唯一
        ensureRoleCodeUnique(request.getCode());

        // 创建角色
        Role role = new Role();
        role.setCode(request.getCode());
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        roleMapper.insert(role);

        log.info("创建角色完成: id={}", role.getId());
        return toVO(role);
    }

    /**
     * 更新指定角色的属性信息。
     *
     * @param roleId  目标角色 ID，不能为 null
     * @param request 包含待修改信息的请求对象，不能为 null
     * @return 更新后的角色视图对象
     * @throws BusinessException 若角色不存在、编码重复或尝试修改预设角色
     */
    @Override
    @Transactional
    public RoleVO updateRole(Long roleId, RoleRequest request) {
        Role role = roleMapper.selectById(roleId);
        BusinessException.throwIf(role == null, UserErrorCode.ROLE_NOT_FOUND);

        // 编码变更时校验唯一
        if (!role.getCode().equals(request.getCode())) {
            BusinessException.throwIf(
                    SYSTEM_ROLE_CODES.contains(role.getCode()),
                    UserErrorCode.SYSTEM_ROLE_PROTECTED
            );
            ensureRoleCodeUnique(request.getCode());
        }

        // 更新角色
        role.setCode(request.getCode());
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        roleMapper.updateById(role);

        log.info("更新角色完成: id={}", roleId);
        return toVO(role);
    }

    /**
     * 删除指定非预设角色，并级联清理关联的用户与权限映射。
     *
     * @param roleId 目标角色 ID，不能为 null
     * @throws BusinessException 若角色不存在或尝试删除系统预设角色
     */
    @Override
    @Transactional
    public void deleteRole(Long roleId) {
        Role role = roleMapper.selectById(roleId);
        BusinessException.throwIf(role == null, UserErrorCode.ROLE_NOT_FOUND);
        BusinessException.throwIf(
                SYSTEM_ROLE_CODES.contains(role.getCode()),
                UserErrorCode.SYSTEM_ROLE_PROTECTED
        );

        // 删除角色权限关联
        LambdaQueryWrapper<RolePermission> rpWrapper = new LambdaQueryWrapper<>();
        rpWrapper.eq(RolePermission::getRoleId, roleId);
        rolePermissionMapper.delete(rpWrapper);

        // 删除用户角色关联
        LambdaQueryWrapper<UserRole> urWrapper = new LambdaQueryWrapper<>();
        urWrapper.eq(UserRole::getRoleId, roleId);
        userRoleMapper.delete(urWrapper);

        // 删除角色
        roleMapper.deleteById(roleId);
        log.info("删除角色完成: id={}", roleId);
    }

    // ==================== 角色权限绑定 ====================

    /**
     * 查询指定角色拥有的全部细粒度权限列表。
     *
     * @param roleId 目标角色 ID，不能为 null
     * @return 权限视图对象列表
     * @throws BusinessException 若角色不存在
     */
    @Override
    public List<PermissionVO> getRolePermissions(Long roleId) {
        // 校验角色存在
        BusinessException.throwIf(roleMapper.selectById(roleId) == null, UserErrorCode.ROLE_NOT_FOUND);

        // 查询角色权限关联
        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermission::getRoleId, roleId);
        List<Long> permissionIds = rolePermissionMapper.selectList(wrapper).stream()
                .map(RolePermission::getPermissionId)
                .distinct()
                .toList();

        // 没有权限时返回空列表
        if (permissionIds.isEmpty()) return List.of();

        return permissionMapper.selectBatchIds(permissionIds).stream()
                .map(this::toPermissionVO)
                .toList();
    }

    /**
     * 全量替换指定角色拥有的权限集合并触发操作审计。
     *
     * @param roleId        目标角色 ID，不能为 null
     * @param permissionIds 权限 ID 列表，不能为 null
     * @throws BusinessException 若角色不存在或部分权限 ID 不存在
     */
    @Override
    @Transactional
    public void updateRolePermissions(Long roleId, List<Long> permissionIds) {
        // 校验角色存在
        BusinessException.throwIf(roleMapper.selectById(roleId) == null, UserErrorCode.ROLE_NOT_FOUND);

        // 权限去重并校验全部存在
        List<Long> distinctPermissionIds = new ArrayList<>(new LinkedHashSet<>(permissionIds));
        if (!distinctPermissionIds.isEmpty()) {
            List<Permission> permissions = permissionMapper.selectBatchIds(distinctPermissionIds);
            BusinessException.throwIf(
                    permissions.size() != distinctPermissionIds.size(),
                    UserErrorCode.PERMISSION_NOT_FOUND
            );
        }

        // 删除旧关联
        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermission::getRoleId, roleId);
        rolePermissionMapper.delete(wrapper);

        // 插入新关联
        for (Long permissionId : distinctPermissionIds) {
            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setPermissionId(permissionId);
            rolePermissionMapper.insert(rolePermission);
        }
        audit.rolePermissionsChanged(roleId, distinctPermissionIds);

        log.info("更新角色权限完成: roleId={}, permissionCount={}",
                roleId, distinctPermissionIds.size());
    }

    // ==================== 私有方法 ====================

    /**
     * 校验角色编码是否全局唯一。
     *
     * @param code 待检查的角色编码，不能为 null
     * @throws BusinessException 若角色编码已存在
     */
    private void ensureRoleCodeUnique(String code) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getCode, code);
        BusinessException.throwIf(roleMapper.selectCount(wrapper) > 0, UserErrorCode.ROLE_CODE_DUPLICATE);
    }

    /**
     * 将角色实体转换为角色 VO。
     *
     * @param role 角色实体
     * @return 角色 VO
     */
    private RoleVO toVO(Role role) {
        return RoleVO.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .createTime(role.getCreateTime())
                .updateTime(role.getUpdateTime())
                .build();
    }

    /**
     * 将权限实体转换为权限 VO。
     *
     * @param permission 权限实体
     * @return 权限 VO
     */
    private PermissionVO toPermissionVO(Permission permission) {
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
