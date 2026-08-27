package com.senior.leetmodelbackend.controller;

import com.senior.leetmodelbackend.common.annotation.RequirePermission;
import com.senior.leetmodelbackend.common.utils.OssUtils;
import com.senior.leetmodelbackend.mapper.OssFileMapper;
import com.senior.leetmodelbackend.pojo.dto.UserUpdateDTO;
import com.senior.leetmodelbackend.pojo.entity.OssFile;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.pojo.entity.User;
import com.senior.leetmodelbackend.pojo.vo.UserVO;
import com.senior.leetmodelbackend.pojo.vo.admin.FileUploadVO;
import com.senior.leetmodelbackend.service.UserService;
import com.senior.leetmodelbackend.validator.user.UserEmailParamValidator;
import com.senior.leetmodelbackend.validator.user.UserIdParamValidator;
import com.senior.leetmodelbackend.validator.user.UserUpdateDTOValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@AllArgsConstructor
public class UserController {

    private final UserService userService;
    private final OssUtils ossUtils;
    private final OssFileMapper ossFileMapper;
    private final UserIdParamValidator userIdParamValidator;
    private final UserEmailParamValidator userEmailParamValidator;
    private final UserUpdateDTOValidator userUpdateDTOValidator;

    @RequirePermission(value = "USER_VIEW", selfAccess = true)
    @GetMapping("/{userId}")
    public Result<UserVO> getUserById(@PathVariable Integer userId) {
        userIdParamValidator.validate(userId);
        User user = userService.getUserById(userId);
        UserVO vo = UserVO.createVO(user);
        resolveAvatarUrl(vo);
        return Result.success(vo);
    }

    @RequirePermission("USER_VIEW")
    @GetMapping("/email/{email}")
    public Result<UserVO> getUserByEmail(@PathVariable String email) {
        userEmailParamValidator.validate(email);
        User user = userService.getUserByEmail(email);
        UserVO vo = UserVO.createVO(user);
        resolveAvatarUrl(vo);
        return Result.success(vo);
    }

    private void resolveAvatarUrl(UserVO vo) {
        if (vo.getAvatarFileId() != null) {
            OssFile ossFile = ossFileMapper.getOssFileById(vo.getAvatarFileId());
            if (ossFile != null) {
                vo.setAvatarUrl(ossFile.getFileUrl());
            }
        }
    }

    @RequirePermission("USER_DELETE")
    @DeleteMapping("/{userId}")
    public Result<Void> deleteUserById(@PathVariable Integer userId) {
        userIdParamValidator.validate(userId);
        userService.deleteUserById(userId);
        return Result.success();
    }

    @RequirePermission(value = "USER_UPDATE", selfAccess = true)
    @PutMapping("/{userId}")
    public Result<Void> updateUserById(@PathVariable Integer userId, @RequestBody UserUpdateDTO dto) {
        userIdParamValidator.validate(userId);
        userUpdateDTOValidator.validate(dto);
        userService.updateUserById(userId, dto);
        return Result.success();
    }

    @RequirePermission(value = "USER_UPDATE", selfAccess = true)
    @PostMapping("/{userId}/avatar")
    public Result<FileUploadVO> uploadAvatar(@PathVariable Integer userId,
                                             @RequestParam MultipartFile file,
                                             HttpServletRequest req) {
        userIdParamValidator.validate(userId);
        String url = ossUtils.uploadFile(file);
        OssFile ossFile = new OssFile();
        ossFile.setFileName(file.getOriginalFilename());
        ossFile.setFileUrl(url);
        String suffix = "";
        String name = file.getOriginalFilename();
        if (name != null && name.contains(".")) {
            suffix = name.substring(name.lastIndexOf(".")).toLowerCase();
        }
        ossFile.setFileSuffix(suffix);
        ossFile.setContentType(file.getContentType());
        ossFile.setFileSize(file.getSize());
        ossFile.setUploaderId(userId);
        ossFileMapper.insertOssFile(ossFile);

        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setAvatarFileId(ossFile.getFileId());
        userService.updateUserById(userId, dto);

        return Result.success(new FileUploadVO(ossFile.getFileId(), url));
    }
}
