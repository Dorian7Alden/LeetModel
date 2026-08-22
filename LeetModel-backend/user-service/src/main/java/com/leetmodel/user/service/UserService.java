package com.leetmodel.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.leetmodel.user.dto.ChangePasswordRequest;
import com.leetmodel.user.dto.UserPageQuery;
import com.leetmodel.user.dto.UserUpdateRequest;
import com.leetmodel.user.entity.User;
import com.leetmodel.user.vo.UserAdminVO;
import com.leetmodel.user.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户服务接口。
 *
 * @author LeetModel
 */
public interface UserService extends IService<User> {

    /**
     * 根据用户名查询用户。
     *
     * @param username 用户名
     * @return 用户实体，不存在则返回 null
     */
    User findByUsername(String username);

    /**
     * 获取用户个人信息（脱敏 VO）。
     *
     * @param userId 用户 ID
     * @return 用户 VO
     */
    UserVO getProfile(Long userId);

    /**
     * 更新个人信息（昵称、邮箱）。
     *
     * @param userId  用户 ID
     * @param request 更新内容
     * @return 更新后的用户 VO
     */
    UserVO updateProfile(Long userId, UserUpdateRequest request);

    /**
     * 修改密码。
     *
     * @param userId  用户 ID
     * @param request 新旧密码
     */
    void changePassword(Long userId, ChangePasswordRequest request);

    /**
     * 上传/更新头像。
     *
     * @param userId 用户 ID
     * @param file   头像文件
     * @return 头像访问 URL
     */
    String updateAvatar(Long userId, MultipartFile file);

    // ==================== 管理员方法 ====================

    /**
     * 分页查询用户列表（管理员）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    IPage<UserAdminVO> listUsers(UserPageQuery query);

    /**
     * 查看用户详情（含角色信息，管理员）。
     *
     * @param userId 用户 ID
     * @return 用户详情 VO
     */
    UserAdminVO getUserDetail(Long userId);

    /**
     * 更新用户状态（启用/禁用，管理员）。
     *
     * @param userId 用户 ID
     * @param status 状态（1=正常 0=禁用）
     */
    void updateStatus(Long userId, Integer status);

    /**
     * 更新用户角色（管理员）。
     *
     * @param userId  用户 ID
     * @param roleIds 角色 ID 列表
     */
    void updateRoles(Long userId, List<Long> roleIds);
}
