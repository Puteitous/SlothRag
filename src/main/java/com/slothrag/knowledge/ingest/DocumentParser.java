package com.slothrag.knowledge.ingest;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

import com.slothrag.knowledge.domain.block.Block;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 文档解析：基于 Apache Tika，支持 PDF/Word/PPT/HTML/TXT/MD 等
 */
@Slf4j
@Component
public class DocumentParser {

    private final Tika tika = new Tika();
    private final TextBlockExtractor textBlockExtractor;

    public DocumentParser(TextBlockExtractor textBlockExtractor) {
        this.textBlockExtractor = textBlockExtractor;
    }

    /**
     * 解析为纯文本（向后兼容）
     */
    public String parse(Path file) {
        try {
            return tika.parseToString(file.toFile());
        } catch (IOException | TikaException e) {
            throw new RuntimeException("文档解析失败: " + file.getFileName() + " - " + e.getMessage(), e);
        }
    }

    /**
     * 解析为结构块列表（Block-Aware 分块用）
     */
    public List<Block> parseToBlocks(Path file) {
        String text = parse(file);
        return textBlockExtractor.extractBlocks(text);
    }
}
