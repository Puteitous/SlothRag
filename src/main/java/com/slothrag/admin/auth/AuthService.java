package com.slothrag.admin.auth;

import com.slothrag.common.web.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 兜底种子账号，仅当 user 表为空时启用 */
    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "admin123";

    private final UserDao userDao;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 校验用户名密码，成功返回 token，失败抛业务异常 */
    public String login(String username, String password) {
        if (username == null || password == null) {
            throw new BizException("400", "用户名或密码不能为空");
        }
        User user = userDao.findByUsername(username);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException("401", "用户名或密码错误");
        }
        return tokenService.issue(user.getUsername());
    }

    /** 从 token 解析当前用户名，供拦截器复用 */
    public String resolveUsername(String token) {
        return tokenService.verify(token);
    }

    /** 启动时兜底：user 表为空则创建默认 admin 账号 */
    public void seedDefaultAdminIfNeeded() {
        if (userDao.count() > 0) return;
        String hash = passwordEncoder.encode(DEFAULT_PASSWORD);
        userDao.insert(DEFAULT_USERNAME, hash, "管理员", "ADMIN");
        log.warn("已创建默认后台账号 {} / {}（请在登录后尽快修改口令）", DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }
}