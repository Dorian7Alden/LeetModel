package com.senior.leetmodelbackend.pojo.vo.admin;

import com.senior.leetmodelbackend.pojo.entity.Permission;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PermissionVO {

    private Long permissionId;
    private String name;
    private String code;
    private String description;
    private Boolean status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static PermissionVO createVO(Permission permission) {
        PermissionVO vo = new PermissionVO();
        vo.setPermissionId(permission.getPermissionId());
        vo.setName(permission.getName());
        vo.setCode(permission.getCode());
        vo.setDescription(permission.getDescription());
        vo.setStatus(permission.getStatus());
        vo.setCreateTime(permission.getCreateTime());
        vo.setUpdateTime(permission.getUpdateTime());
        return vo;
    }
}
