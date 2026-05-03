package com.senior.leetmodelbackend.pojo.vo;

import com.senior.leetmodelbackend.pojo.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {

    private Integer id;
    private String username;
    private String email;
    private String school;
    private String phone;
    private Integer avatarFileId;
    private String avatarUrl;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static UserVO createVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getUserId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setSchool(user.getSchool());
        vo.setPhone(user.getPhone());
        vo.setAvatarFileId(user.getAvatarFileId());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }
}
