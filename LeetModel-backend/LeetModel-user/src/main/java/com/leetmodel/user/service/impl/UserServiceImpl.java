package com.leetmodel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.user.dto.ChangePasswordRequest;
import com.leetmodel.user.dto.UserUpdateRequest;
import com.leetmodel.user.entity.User;
import com.leetmodel.user.enums.UserErrorCode;
import com.leetmodel.user.mapper.UserMapper;
import com.leetmodel.user.service.UserService;
import com.leetmodel.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户服务实现。
 *
 * @author LeetModel
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;

    /**
     * StorageService 为可选依赖 —— 未启用 MinIO 时此处为 null，
     * 此时上传头像会抛出明确错误而非 NPE。
     */
    @Autowired(required = false)
    private StorageService storageService;

    @Override
    public User findByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    @Override
    public UserVO getProfile(Long userId) {
        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);
        return toVO(user);
    }

    @Override
    public UserVO updateProfile(Long userId, UserUpdateRequest request) {
        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);

        boolean changed = false;
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
            changed = true;
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
            changed = true;
        }
        if (changed) {
            updateById(user);
            log.info("用户 {} 更新个人信息", userId);
        }
        return toVO(user);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);

        // 校验旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(UserErrorCode.PASSWORD_OLD_INVALID);
        }

        // 新旧密码不能相同
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new BusinessException(UserErrorCode.PASSWORD_SAME_AS_OLD);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        updateById(user);
        log.info("用户 {} 修改密码", userId);
    }

    @Override
    public String updateAvatar(Long userId, MultipartFile file) {
        if (storageService == null) {
            throw new BusinessException(UserErrorCode.STORAGE_NOT_ENABLED);
        }

        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);

        String objectName = storageService.upload(file, "avatars");
        String avatarUrl = storageService.getUrl(objectName);

        user.setAvatarUrl(avatarUrl);
        updateById(user);
        log.info("用户 {} 更新头像: {}", userId, objectName);

        return avatarUrl;
    }

    // ==================== 私有方法 ====================

    private UserVO toVO(User user) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .build();
    }
}
