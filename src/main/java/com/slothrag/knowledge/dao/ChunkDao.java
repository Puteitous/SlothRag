package com.slothrag.knowledge.dao;

import com.pgvector.PGvector;
import com.slothrag.knowledge.domain.Chunk;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 分块 DAO（含 pgvector 向量批量写入与检索）
 */
@Repository
@RequiredArgsConstructor
public class ChunkDao {

    private final JdbcTemplate jdbc;

    /**
     * 检索命中项
     */
    public record SearchHit(Long id, Long docId, String content, double score, String headingPath, String docName) {
    }

    public void batchInsert(List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO chunk (doc_id, kb_id, seq, content, heading_path, vector) VALUES (?, ?, ?, ?, ?, ?)";
        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Chunk chunk = chunks.get(i);
                ps.setLong(1, chunk.getDocId());
                ps.setLong(2, chunk.getKbId());
                ps.setInt(3, chunk.getSeq());
                ps.setString(4, chunk.getContent());
                ps.setString(5, chunk.getHeadingPath());
                ps.setObject(6, chunk.getVector() == null ? null : new PGvector(chunk.getVector()));
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        });
    }

    /**
     * 向量检索：余弦相似度 top-k（HNSW 索引）
     */
    public List<SearchHit> vectorSearch(float[] queryVector, Long kbId, int topK) {
        String sql = """
                SELECT c.id, c.doc_id, c.content, c.heading_path,
                       1 - (c.vector <=> ?) AS score,
                       d.file_name AS doc_name
                FROM chunk c
                LEFT JOIN doc d ON d.id = c.doc_id
                WHERE c.kb_id = ?
                ORDER BY c.vector <=> ?
                LIMIT ?
                """;
        return jdbc.query(sql, (rs, i) -> new SearchHit(
                        rs.getLong("id"),
                        rs.getLong("doc_id"),
                        rs.getString("content"),
                        rs.getDouble("score"),
                        rs.getString("heading_path"),
                        rs.getString("doc_name")),
                new PGvector(queryVector), kbId, new PGvector(queryVector), topK);
    }

    /**
     * 关键词检索：使用 pg_jieba 中文分词全文检索。
     * 将原始问题直接传给 plainto_tsquery，由 jieba 引擎自动分词。
     * 搭配 GIN 索引 idx_chunk_content_fts（to_tsvector('jiebacfg', content)）。
     *
     * @param query 用户原始问题（无需预分词）
     */
    public List<SearchHit> keywordSearch(String query, Long kbId, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String sql = """
                SELECT c.id, c.doc_id, c.content, c.heading_path,
                       ts_rank(to_tsvector('jiebacfg', c.content), plainto_tsquery('jiebacfg', ?)) AS score,
                       d.file_name AS doc_name
                FROM chunk c
                LEFT JOIN doc d ON d.id = c.doc_id
                WHERE c.kb_id = ?
                  AND to_tsvector('jiebacfg', c.content) @@ plainto_tsquery('jiebacfg', ?)
                ORDER BY score DESC
                LIMIT ?
                """;
        return jdbc.query(sql, (rs, i) -> new SearchHit(
                        rs.getLong("id"),
                        rs.getLong("doc_id"),
                        rs.getString("content"),
                        rs.getDouble("score"),
                        rs.getString("heading_path"),
                        rs.getString("doc_name")),
                query, kbId, query, topK);
    }

    /**
     * 按文档 ID + 序号范围读取切片（用于 read_doc 工具）
     */
    public List<Chunk> readDoc(Long docId, Integer seqStart, Integer seqEnd) {
        String sql = """
                SELECT c.id, c.doc_id, c.kb_id, c.seq, c.content, c.heading_path, c.created_at
                FROM chunk c
                WHERE c.doc_id = ? AND c.seq BETWEEN ? AND ?
                ORDER BY c.seq
                """;
        return jdbc.query(sql, (rs, i) -> {
            Chunk c = new Chunk();
            c.setId(rs.getLong("id"));
            c.setDocId(rs.getLong("doc_id"));
            c.setKbId(rs.getLong("kb_id"));
            c.setSeq(rs.getInt("seq"));
            c.setContent(rs.getString("content"));
            c.setHeadingPath(rs.getString("heading_path"));
            c.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            return c;
        }, docId, seqStart, seqEnd);
    }

    /**
     * 在指定文档内搜索关键词（ILIKE 模糊匹配），用于 grep_doc 工具
     */
    public List<Chunk> grepDoc(Long docId, String keyword) {
        String sql = """
                SELECT c.id, c.doc_id, c.kb_id, c.seq, c.content, c.heading_path, c.created_at
                FROM chunk c
                WHERE c.doc_id = ? AND c.content ILIKE ? ESCAPE '\\'
                ORDER BY c.seq
                """;
        String pattern = "%" + escapeLike(keyword) + "%";
        return jdbc.query(sql, (rs, i) -> {
            Chunk c = new Chunk();
            c.setId(rs.getLong("id"));
            c.setDocId(rs.getLong("doc_id"));
            c.setKbId(rs.getLong("kb_id"));
            c.setSeq(rs.getInt("seq"));
            c.setContent(rs.getString("content"));
            c.setHeadingPath(rs.getString("heading_path"));
            c.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            return c;
        }, docId, pattern);
    }

    private String escapeLike(String text) {
        return text.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
