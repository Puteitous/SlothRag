package com.ragbase.knowledge.dao;

import com.ragbase.knowledge.domain.Doc;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文档 DAO
 */
@Repository
@RequiredArgsConstructor
public class DocDao {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Doc> MAPPER = (rs, i) -> {
        Doc doc = new Doc();
        doc.setId(rs.getLong("id"));
        doc.setKbId(rs.getLong("kb_id"));
        doc.setFileName(rs.getString("file_name"));
        doc.setFilePath(rs.getString("file_path"));
        doc.setFileSize(rs.getLong("file_size"));
        doc.setStatus(rs.getString("status"));
        doc.setChunkCount(rs.getInt("chunk_count"));
        doc.setErrorMsg(rs.getString("error_msg"));
        doc.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        doc.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return doc;
    };

    public Long insert(Doc doc) {
        String sql = "INSERT INTO doc (kb_id, file_name, file_path, file_size, status) " +
                "VALUES (?, ?, ?, ?, ?) RETURNING id";
        return jdbc.queryForObject(sql, Long.class,
                doc.getKbId(), doc.getFileName(), doc.getFilePath(), doc.getFileSize(), doc.getStatus());
    }

    public void updateStatus(Long id, String status) {
        jdbc.update("UPDATE doc SET status = ?, updated_at = now() WHERE id = ?", status, id);
    }

    public void updateChunkCount(Long id, int chunkCount) {
        jdbc.update("UPDATE doc SET chunk_count = ?, status = ?, updated_at = now() WHERE id = ?",
                chunkCount, Doc.STATUS_INDEXED, id);
    }

    public void markFailed(Long id, String errorMsg) {
        jdbc.update("UPDATE doc SET status = ?, error_msg = ?, updated_at = now() WHERE id = ?",
                Doc.STATUS_FAILED, errorMsg, id);
    }

    public Doc findById(Long id) {
        return jdbc.query("SELECT * FROM doc WHERE id = ?", MAPPER, id).stream()
                .findFirst().orElse(null);
    }

    /**
     * 重置文档状态为待处理并清空错误信息，供失败重试使用
     */
    public void resetStatus(Long id) {
        jdbc.update("UPDATE doc SET status = ?, error_msg = NULL, updated_at = now() WHERE id = ?",
                Doc.STATUS_PENDING, id);
    }

    /**
     * 分页查询库内文档，按创建时间倒序
     */
    public List<Doc> listByKbId(Long kbId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return jdbc.query("SELECT * FROM doc WHERE kb_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                MAPPER, kbId, pageSize, offset);
    }

    /**
     * 统计库内文档数量
     */
    public long countByKbId(Long kbId) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM doc WHERE kb_id = ?", Long.class, kbId);
        return total == null ? 0 : total;
    }

    /**
     * 删除文档（校验归属），chunk 由外键 ON DELETE CASCADE 级联删除
     *
     * @return 影响行数，0 表示文档不存在或不属于该库
     */
    public int deleteByIdAndKbId(Long id, Long kbId) {
        return jdbc.update("DELETE FROM doc WHERE id = ? AND kb_id = ?", id, kbId);
    }
}
