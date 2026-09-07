package com.slothrag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理后台认证配置
 */
@Data
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    /** Token 签名密钥（HS256 用，需足够长） */
    private String tokenSecret;

    /** Token 有效期（小时） */
    private long tokenTtlHours = 24;
}