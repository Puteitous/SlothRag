package com.ragbase.knowledge.dao;

import com.ragbase.knowledge.domain.IngestTask;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 入库任务 DAO
 */
@Repository
@RequiredArgsConstructor
public class IngestTaskDao {

    private final JdbcTemplate jdbc;

    private static final RowMapper<IngestTask> MAPPER = (rs, i) -> {
        IngestTask t = new IngestTask();
        t.setId(rs.getLong("id"));
        t.setDocId(rs.getLong("doc_id"));
        t.setKbId(rs.getLong("kb_id"));
        t.setStatus(rs.getString("status"));
        t.setProgress(rs.getInt("progress"));
        t.setCurrentStage(rs.getString("current_stage"));
        t.setErrorMsg(rs.getString("error_msg"));
        t.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        t.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return t;
    };

    public Long insert(IngestTask task) {
        String sql = "INSERT INTO ingest_task (doc_id, kb_id, status, progress) VALUES (?, ?, ?, ?) RETURNING id";
        return jdbc.queryForObject(sql, Long.class,
                task.getDocId(), task.getKbId(), task.getStatus(), task.getProgress());
    }

    public void updateProgress(Long id, int progress, String stage) {
        jdbc.update("UPDATE ingest_task SET progress = ?, current_stage = ?, updated_at = now() WHERE id = ?",
                progress, stage, id);
    }

    public void updateStatus(Long id, String status, String errorMsg) {
        jdbc.update("UPDATE ingest_task SET status = ?, error_msg = ?, updated_at = now() WHERE id = ?",
                status, errorMsg, id);
    }

    /**
     * 分页查询任务，按创建时间倒序
     *
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数
     * @param status   状态过滤，可为 null/空
     */
    public List<IngestTask> page(int page, int pageSize, String status) {
        int offset = (page - 1) * pageSize;
        String sql = "SELECT * FROM ingest_task";
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(status)) {
            sql += " WHERE status = ?";
            args.add(status);
        }
        sql += " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        args.add(pageSize);
        args.add(offset);
        return jdbc.query(sql, MAPPER, args.toArray());
    }

    /**
     * 按状态统计任务数量
     */
    public long count(String status) {
        if (StringUtils.hasText(status)) {
            Long total = jdbc.queryForObject("SELECT count(*) FROM ingest_task WHERE status = ?", Long.class, status);
            return total == null ? 0 : total;
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM ingest_task", Long.class);
        return total == null ? 0 : total;
    }

    /**
     * 重置任务状态为排队中，供失败重试使用
     */
    public void resetForRetry(Long id) {
        jdbc.update("UPDATE ingest_task SET status = ?, progress = 0, current_stage = NULL, " +
                        "error_msg = NULL, updated_at = now() WHERE id = ?",
                IngestTask.STATUS_QUEUED, id);
    }

    /**
     * 统计某知识库下指定状态的任务数
     */
    public long countByKbIdAndStatus(Long kbId, String status) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM ingest_task WHERE kb_id = ? AND status = ?",
                Long.class, kbId, status);
        return n == null ? 0 : n;
    }

    /**
     * 统计某文档下指定状态的任务数
     */
    public long countByDocIdAndStatus(Long docId, String status) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM ingest_task WHERE doc_id = ? AND status = ?",
                Long.class, docId, status);
        return n == null ? 0 : n;
    }

    public void deleteById(Long id) {
        jdbc.update("DELETE FROM ingest_task WHERE id = ?", id);
    }

    public IngestTask findById(Long id) {
        return jdbc.query("SELECT * FROM ingest_task WHERE id = ?", MAPPER, id).stream()
                .findFirst().orElse(null);
    }
}
