package com.slothrag.admin.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 管理后台认证 Web 配置：
 *  - 拦截 /api/kb/** 要求携带有效 token
 *  - 启动时兜底创建默认 admin 账号
 */
@Configuration
@RequiredArgsConstructor
public class AuthWebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/kb/**");
    }

    @Bean
    ApplicationRunner seedDefaultAdmin(AuthService authService) {
        return args -> authService.seedDefaultAdminIfNeeded();
    }
}