package com.ragbase.knowledge.ingest;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 文档解析：基于 Apache Tika，支持 PDF/Word/PPT/HTML/TXT/MD 等
 */
@Slf4j
@Component
public class DocumentParser {

    private final Tika tika = new Tika();

    public String parse(Path file) {
        try {
            return tika.parseToString(file.toFile());
        } catch (IOException | TikaException e) {
            throw new RuntimeException("文档解析失败: " + file.getFileName() + " - " + e.getMessage(), e);
        }
    }
}
