package com.senior.leetmodelbackend.pojo.vo.admin;

import com.senior.leetmodelbackend.pojo.entity.Role;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoleVO {

    private Long roleId;
    private String name;
    private String code;
    private String description;
    private Boolean status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static RoleVO createVO(Role role) {
        RoleVO vo = new RoleVO();
        vo.setRoleId(role.getRoleId());
        vo.setName(role.getName());
        vo.setCode(role.getCode());
        vo.setDescription(role.getDescription());
        vo.setStatus(role.getStatus());
        vo.setCreateTime(role.getCreateTime());
        vo.setUpdateTime(role.getUpdateTime());
        return vo;
    }
}
