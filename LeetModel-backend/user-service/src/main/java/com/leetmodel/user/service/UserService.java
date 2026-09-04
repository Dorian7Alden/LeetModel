package com.leetmodel.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.leetmodel.user.dto.ChangePasswordRequest;
import com.leetmodel.common.api.dto.UserPageQuery;
import com.leetmodel.user.dto.UserUpdateRequest;
import com.leetmodel.user.entity.User;
import com.leetmodel.common.api.vo.UserAdminVO;
import com.leetmodel.user.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户服务接口。
 */
public interface UserService extends IService<User> {

    /**
     * 根据用户名查询用户。
     *
     * @param username 用户名，不能为 null
     * @return 用户实体，不存在则返回 null
     */
    User findByUsername(String username);

    /**
     * 获取用户个人信息（脱敏 VO）。
     *
     * @param userId 目标用户 ID，不能为 null
     * @return 用户脱敏 VO
     * @throws com.leetmodel.common.core.exception.BusinessException 若用户不存在
     */
    UserVO getProfile(Long userId);

    /**
     * 更新个人信息（昵称、邮箱）。
     *
     * @param userId  目标用户 ID，不能为 null
     * @param request 更新内容请求对象，不能为 null
     * @return 更新后的用户 VO
     * @throws com.leetmodel.common.core.exception.BusinessException 若用户不存在
     */
    UserVO updateProfile(Long userId, UserUpdateRequest request);

    /**
     * 修改密码。
     *
     * @param userId  目标用户 ID，不能为 null
     * @param request 包含新旧密码的请求对象，不能为 null
     * @throws com.leetmodel.common.core.exception.BusinessException 若用户不存在、原密码错误或新旧密码相同
     */
    void changePassword(Long userId, ChangePasswordRequest request);

    /**
     * 上传/更新头像。
     *
     * @param userId 目标用户 ID，不能为 null
     * @param file   头像文件，不能为 null
     * @return 头像访问 URL
     * @throws com.leetmodel.common.core.exception.BusinessException 若对象存储未启用或上传失败
     */
    String updateAvatar(Long userId, MultipartFile file);

    // ==================== 管理员方法 ====================

    /**
     * 分页查询用户列表（管理员）。
     *
     * @param query 查询条件对象，不能为 null
     * @return 分页结果对象
     */
    IPage<UserAdminVO> listUsers(UserPageQuery query);

    /**
     * 查看用户详情（含角色信息，管理员）。
     *
     * @param userId 目标用户 ID，不能为 null
     * @return 用户详情 VO
     * @throws com.leetmodel.common.core.exception.BusinessException 若用户不存在
     */
    UserAdminVO getUserDetail(Long userId);

    /**
     * 更新用户状态（启用/禁用，管理员）。
     *
     * @param userId 目标用户 ID，不能为 null
     * @param status 状态（1=正常 0=禁用）
     * @throws com.leetmodel.common.core.exception.BusinessException 若用户不存在
     */
    void updateStatus(Long userId, Integer status);

    /**
     * 更新用户角色（管理员）。
     *
     * @param userId  目标用户 ID，不能为 null
     * @param roleIds 角色 ID 列表，不能为 null
     * @throws com.leetmodel.common.core.exception.BusinessException 若用户不存在或角色不存在
     */
    void updateRoles(Long userId, List<Long> roleIds);
}
