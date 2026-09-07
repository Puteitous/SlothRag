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
     * 关键词检索：对多个关键词做 LIKE 子串匹配（OR），中文可直接命中
     *
     * @param keywords 切词后的关键词列表
     */
    public List<SearchHit> keywordSearch(List<String> keywords, Long kbId, int topK) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.doc_id, c.content, c.heading_path,
                       1.0 AS score,
                       d.file_name AS doc_name
                FROM chunk c
                LEFT JOIN doc d ON d.id = c.doc_id
                WHERE c.kb_id = ? AND (
                """);
        List<Object> args = new ArrayList<>();
        args.add(kbId);
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("c.content LIKE ? ESCAPE '\\'");
            args.add("%" + escapeLike(keywords.get(i)) + "%");
        }
        sql.append(") ORDER BY c.seq LIMIT ?");
        args.add(topK);
        return jdbc.query(sql.toString(), (rs, i) -> new SearchHit(
                        rs.getLong("id"),
                        rs.getLong("doc_id"),
                        rs.getString("content"),
                        rs.getDouble("score"),
                        rs.getString("heading_path"),
                        rs.getString("doc_name")),
                args.toArray());
    }

    private String escapeLike(String text) {
        return text.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
