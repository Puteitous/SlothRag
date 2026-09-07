package com.slothrag.admin.auth;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台用户
 */
@Data
public class User {

    private Long id;
    private String username;
    private String password; // bcrypt hash
    private String nickname;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}