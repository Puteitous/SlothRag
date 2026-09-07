package com.ragbase.admin.auth;

import com.ragbase.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 轻量 Token 签发 / 校验（HS256 风格，无外部依赖）。
 *
 * 格式: base64url(username) + ":" + 过期时间戳(ms) + ":" + hex(hmac)
 * HMAC 覆盖 "base64url(username):过期时间戳"，密钥取自配置 auth.token-secret。
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String HMAC_ALGO = "HmacSHA256";

    private final AuthProperties props;

    /** 生成 token，返回明文串 */
    public String issue(String username) {
        long expireAt = System.currentTimeMillis() + props.getTokenTtlHours() * 3600_000L;
        String payload = username + ":" + expireAt;
        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String sig = hmac(payloadB64);
        return payloadB64 + "." + sig;
    }

    /**
     * 校验 token，合法返回用户名，否则返回 null。
     */
    public String verify(String token) {
        if (token == null) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 2) return null;
        String payloadB64 = parts[0];
        String sig = parts[1];
        if (!constantTimeEq(sig, hmac(payloadB64))) return null;
        try {
            String payload = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
            int idx = payload.lastIndexOf(':');
            long expireAt = Long.parseLong(payload.substring(idx + 1));
            if (System.currentTimeMillis() >= expireAt) return null;
            return payload.substring(0, idx);
        } catch (Exception e) {
            return null;
        }
    }

    private String hmac(String payloadB64) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(props.getTokenSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] raw = mac.doFinal(payloadB64.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    private boolean constantTimeEq(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}