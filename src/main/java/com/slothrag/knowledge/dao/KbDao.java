package com.slothrag.knowledge.dao;

import com.slothrag.knowledge.domain.Kb;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 知识库 DAO
 */
@Repository
@RequiredArgsConstructor
public class KbDao {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Kb> MAPPER = (rs, i) -> {
        Kb kb = new Kb();
        kb.setId(rs.getLong("id"));
        kb.setName(rs.getString("name"));
        kb.setDescription(rs.getString("description"));
        kb.setEmbeddingModel(rs.getString("embedding_model"));
        kb.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        kb.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return kb;
    };

    public Long insert(Kb kb) {
        String sql = "INSERT INTO kb (name, description, embedding_model) VALUES (?, ?, ?) RETURNING id";
        return jdbc.queryForObject(sql, Long.class,
                kb.getName(), kb.getDescription(), kb.getEmbeddingModel());
    }

    public Kb findById(Long id) {
        return jdbc.query("SELECT * FROM kb WHERE id = ?", MAPPER, id).stream()
                .findFirst().orElse(null);
    }

    /**
     * 库列表（含文档数），按创建时间倒序
     */
    public List<Kb> listAll() {
        String sql = """
                SELECT kb.id, kb.name, kb.description, kb.embedding_model,
                       kb.created_at, kb.updated_at, COUNT(doc.id) AS doc_count
                FROM kb LEFT JOIN doc ON doc.kb_id = kb.id
                GROUP BY kb.id
                ORDER BY kb.created_at DESC
                """;
        return jdbc.query(sql, (rs, i) -> {
            Kb kb = MAPPER.mapRow(rs, i);
            kb.setDocCount(rs.getInt("doc_count"));
            return kb;
        });
    }

    public void deleteById(Long id) {
        // doc/chunk/ingest_task 由外键 ON DELETE CASCADE 级联删除
        jdbc.update("DELETE FROM kb WHERE id = ?", id);
    }
}
