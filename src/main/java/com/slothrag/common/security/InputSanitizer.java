package com.slothrag.common.security;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 用户输入清洗 & Prompt 注入检测。
 * <p>
 * 不做过度过滤（不拦正常中文/英文/标点），只处理：
 * <ul>
 *   <li>长度截断</li>
 *   <li>空白规整</li>
 *   <li>常见的 prompt 注入特征标记（用于统计/告警，不阻断）</li>
 * </ul>
 * <p>
 * 核心防御策略：不依赖"特征过滤"来防注入（攻防不对称），
 * 而是靠 system prompt 加固 + 输入长度限制 + 后续检索证据闸门兜底。
 */
@Slf4j
public class InputSanitizer {

    /** 默认最大输入长度（字符数） */
    public static final int DEFAULT_MAX_LENGTH = 2000;

    /** 最大输入硬上限（防止极端超长文本绕过配置） */
    public static final int ABSOLUTE_MAX_LENGTH = 10000;

    /** 注入特征模式列表（仅检测记录，不阻断） */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|above|prior)\\s+(instructions|prompts|directives|messages)"),
            Pattern.compile("(?i)forget\\s+(all\\s+)?(previous|above|prior)\\s+(instructions|prompts|directives)"),
            Pattern.compile("(?i)system\\s+prompt"),
            Pattern.compile("(?i)you\\s+are\\s+(now|no longer)"),
            Pattern.compile("(?i)act\\s+as\\s+if"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?(previous|above)"),
            Pattern.compile("(?i)new\\s+(instructions|prompt|rule)s?\\s*:")
    );

    private InputSanitizer() {
    }

    /**
     * 清洗输入：去首尾空白、规整内部空白、截断长度。
     *
     * @param input     原始用户输入
     * @param maxLength 最大字符数（超出截断），传入 {@code <=0} 时使用默认值
     * @return 清洗后的文本（不会为 null，原始空白输入返回空串）
     */
    public static String sanitize(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        // 规整内部空白：连续空白符压缩为单个空格
        String normalized = trimmed.replaceAll("[\\p{Zs}\\t\\r\\n]+", " ");
        // 长度限制
        int limit = maxLength > 0 ? Math.min(maxLength, ABSOLUTE_MAX_LENGTH) : DEFAULT_MAX_LENGTH;
        if (normalized.length() > limit) {
            log.info("输入超长截断: {} chars → {} chars", normalized.length(), limit);
            normalized = normalized.substring(0, limit);
        }
        return normalized;
    }

    /**
     * 检查并记录注入特征（不阻断，仅告警统计）。
     *
     * @param input 已清洗的输入
     * @return true 如果命中了至少一个注入特征模式
     */
    public static boolean hasInjectionPattern(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.warn("检测到 prompt 注入特征: input=[{}] pattern={}", truncate(input, 100), pattern);
                return true;
            }
        }
        return false;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
