package com.senior.leetmodelbackend.pojo.vo;

import com.senior.leetmodelbackend.pojo.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginVO {

    private String token;
    private Integer id;
    private String email;
    private String username;
    private String role;
    private String school;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static LoginVO createVO(User user, String role, String token) {
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setId(user.getUserId());
        vo.setEmail(user.getEmail());
        vo.setUsername(user.getUsername());
        vo.setRole(role);
        vo.setSchool(user.getSchool());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }
}
