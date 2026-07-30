package com.leetmodel.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.leetmodel.user.dto.ChangePasswordRequest;
import com.leetmodel.user.dto.UserUpdateRequest;
import com.leetmodel.user.entity.User;
import com.leetmodel.user.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

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
}
