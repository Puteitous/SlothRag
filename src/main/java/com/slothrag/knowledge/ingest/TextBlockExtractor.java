package com.slothrag.knowledge.ingest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.slothrag.knowledge.domain.block.Block;
import com.slothrag.knowledge.domain.block.CodeBlock;
import com.slothrag.knowledge.domain.block.HeadingBlock;
import com.slothrag.knowledge.domain.block.ParagraphBlock;

/**
 * 文本 → Block 提取器：从纯文本中识别 Markdown 风格的标题/代码块/段落
 * <p>
 * 支持：
 * - # ## ### #### 等 ATX 式标题
 * - === / --- 下划线式标题（H1 / H2）
 * - ``` 围栏式代码块
 * - 普通段落
 */
@Slf4j
@Component
public class TextBlockExtractor {

    /** 围栏代码块：```...``` 或 ~~~...~~~ */
    private static final Pattern FENCED_CODE = Pattern.compile(
            "^[ \\t]*(```+|~~~+)[ \\t]*(\\w*)[ \\t]*$",
            Pattern.MULTILINE);

    /** ATX 标题：^### 标题 */
    private static final Pattern ATX_HEADING = Pattern.compile(
            "^(#{1,6})\\s+(.+)$",
            Pattern.MULTILINE);

    /**
     * 将纯文本解析为 Block 列表
     *
     * @param text 原始文档文本（Tika 输出）
     * @return Block 列表
     */
    public List<Block> extractBlocks(String text) {
        List<Block> blocks = new ArrayList<>();

        // 1. 先提取围栏代码块，用占位符替换，避免干扰后续解析
        List<String> codeBlocks = new ArrayList<>();
        String withoutCode = extractFencedCodeBlocks(text, codeBlocks);

        // 2. 按行解析剩余文本
        String[] lines = withoutCode.split("\n", -1);
        StringBuilder paragraphBuf = new StringBuilder();
        boolean inCodeBlock = false;
        int codeIdx = 0;

        for (String rawLine : lines) {
            String line = rawLine.stripTrailing();

            // 检查是否是代码块占位符
            if (line.startsWith("\u0000CODE:")) {
                // 将之前缓存的段落先输出
                flushParagraph(paragraphBuf, blocks);
                // 输出代码块
                blocks.add(new CodeBlock("", codeBlocks.get(codeIdx++)));
                continue;
            }

            // 检查是否是 ATX 标题
            Matcher hm = ATX_HEADING.matcher(line);
            if (hm.find()) {
                flushParagraph(paragraphBuf, blocks);
                int level = hm.group(1).length();
                String titleText = hm.group(2).trim();
                blocks.add(new HeadingBlock(level, titleText, titleText + "\n"));
                continue;
            }

            // 检查是否是下划线式标题
            // （独占一行且只有 === 或 ---）
            if (isUnderlineHeading(line)) {
                // 上一条 Block 是段落？升级为 H1/H2
                if (!blocks.isEmpty() && blocks.getLast() instanceof ParagraphBlock lastP) {
                    String prevText = lastP.text().trim();
                    // 替换最后一个段落为标题
                    blocks.removeLast();
                    int level = line.charAt(0) == '=' ? 1 : 2;
                    blocks.add(new HeadingBlock(level, prevText, prevText + "\n"));
                }
                continue;
            }

            // 普通行 → 追加到段落缓冲区
            if (line.trim().isEmpty()) {
                // 空行 → flush 段落（段落分隔标志）
                flushParagraph(paragraphBuf, blocks);
            } else {
                if (!paragraphBuf.isEmpty()) {
                    paragraphBuf.append('\n');
                }
                paragraphBuf.append(line);
            }
        }

        // flush 末尾段落
        flushParagraph(paragraphBuf, blocks);

        log.info("TextBlockExtractor: {} chars → {} blocks", text.length(), blocks.size());
        return blocks;
    }

    /**
     * 提取围栏代码块，替换为占位符
     */
    String extractFencedCodeBlocks(String text, List<String> codeBlocks) {
        Matcher m = FENCED_CODE.matcher(text);
        StringBuffer sb = new StringBuffer();
        int lastEnd = 0;

        while (m.find()) {
            // 代码块之前的内容
            sb.append(text, lastEnd, m.start());

            String fence = m.group(1);
            String lang = m.group(2).trim();

            // 找结束符：下一个 ``` 或 ~~~
            String endFence = fence.substring(0, 3);
            int endIdx = text.indexOf(endFence, m.end());
            if (endIdx < 0) {
                // 无闭合 → 跳过该行当作普通文本
                lastEnd = m.end();
                continue;
            }

            String codeContent = text.substring(m.end(), endIdx);
            // 移除前后空白
            codeContent = codeContent.stripLeading();

            codeBlocks.add(codeContent);

            // 占位符（\u0000 确保不会出现在正常文本中）
            sb.append("\u0000CODE:").append(codeBlocks.size() - 1).append("\n");

            // 关键：将 Matcher 的搜索区域重置到闭合 fence 之后，
            // 避免下一次 m.find() 把闭合 ``` 误当作新的 fence
            lastEnd = endIdx + endFence.length();
            m.region(lastEnd, text.length());
        }

        sb.append(text.substring(lastEnd));
        return sb.toString();
    }

    /**
     * 判断是否为下划线式标题（连续 === 或 ---，且长度 ≥ 3）
     */
    private boolean isUnderlineHeading(String line) {
        if (line.length() < 3) return false;
        char first = line.charAt(0);
        if (first != '=' && first != '-') return false;
        for (int i = 1; i < line.length(); i++) {
            if (line.charAt(i) != first) return false;
        }
        return true;
    }

    private void flushParagraph(StringBuilder buf, List<Block> blocks) {
        if (!buf.isEmpty()) {
            String text = buf.toString().trim();
            if (!text.isEmpty()) {
                blocks.add(new ParagraphBlock(text));
            }
            buf.setLength(0);
        }
    }
}
