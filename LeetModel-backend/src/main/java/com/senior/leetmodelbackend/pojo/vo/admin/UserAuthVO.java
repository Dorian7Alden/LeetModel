package com.senior.leetmodelbackend.pojo.vo.admin;

import com.senior.leetmodelbackend.pojo.entity.Role;
import lombok.Data;
import java.util.List;

@Data
public class UserAuthVO {
    private Long userId;
    private String username;
    private String email;
    private List<Role> roles;
}
