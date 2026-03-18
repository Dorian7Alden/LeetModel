package com.senior.leetmodelbackend.pojo;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

@Data
public class User {

    @Id
    private Integer id;
    private String username;
    private String email;
    @JsonIgnore
    private String password;
    private String school;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String phone; // TODO: 响应数据的时候保护隐私，防止输出完整的手机号
    private String role;
    private String trainerType;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
