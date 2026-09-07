package com.slothrag.knowledge.service;

import com.slothrag.common.web.BizException;
import com.slothrag.common.web.PageResult;
import com.slothrag.knowledge.dao.DocDao;
import com.slothrag.knowledge.dao.IngestTaskDao;
import com.slothrag.knowledge.dao.KbDao;
import com.slothrag.knowledge.domain.Doc;
import com.slothrag.knowledge.domain.IngestTask;
import com.slothrag.knowledge.domain.Kb;
import com.slothrag.knowledge.ingest.IngestAsyncRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 知识库与文档入库服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KbDao kbDao;
    private final DocDao docDao;
    private final IngestTaskDao ingestTaskDao;
    private final IngestAsyncRunner ingestAsyncRunner;

    @Value("${kb.storage-dir}")
    private String storageDir;

    private static final Set<String> TASK_STATUSES = Set.of(
            IngestTask.STATUS_QUEUED, IngestTask.STATUS_RUNNING,
            IngestTask.STATUS_SUCCESS, IngestTask.STATUS_FAILED);

    public Long createKb(String name, String description) {
        Kb kb = new Kb();
        kb.setName(name);
        kb.setDescription(description);
        kb.setEmbeddingModel("BAAI/bge-m3");
        return kbDao.insert(kb);
    }

    /**
     * 上传文档并异步执行入库，返回入库任务 id
     */
    public Long uploadDocument(Long kbId, MultipartFile file) {
        Kb kb = kbDao.findById(kbId);
        if (kb == null) {
            throw new BizException("404", "知识库不存在");
        }

        Path savedPath = saveFile(kbId, file);

        Doc doc = new Doc();
        doc.setKbId(kbId);
        doc.setFileName(file.getOriginalFilename());
        doc.setFilePath(savedPath.toString());
        doc.setFileSize(file.getSize());
        doc.setStatus(Doc.STATUS_PENDING);
        Long docId = docDao.insert(doc);

        IngestTask task = new IngestTask();
        task.setDocId(docId);
        task.setKbId(kbId);
        task.setStatus(IngestTask.STATUS_QUEUED);
        task.setProgress(0);
        Long taskId = ingestTaskDao.insert(task);

        ingestAsyncRunner.runAsync(docId, kbId, savedPath, taskId);
        return taskId;
    }

    public IngestTask getTask(Long taskId) {
        return ingestTaskDao.findById(taskId);
    }

    /**
     * 分页查询入库任务，可按状态过滤
     */
    public PageResult<IngestTask> listTasks(int page, int pageSize, String status) {
        validateStatus(status);
        long total = ingestTaskDao.count(status);
        List<IngestTask> list = ingestTaskDao.page(page, pageSize, status);
        return new PageResult<>(list, total, page, pageSize);
    }

    /**
     * 重试失败入库任务：重置任务与文档状态后重新异步执行
     */
    public void retryTask(Long taskId) {
        IngestTask task = ingestTaskDao.findById(taskId);
        if (task == null) {
            throw new BizException("404", "任务不存在");
        }
        if (!IngestTask.STATUS_FAILED.equals(task.getStatus())) {
            throw new BizException("INVALID_STATE", "仅失败任务可重试");
        }
        Doc doc = docDao.findById(task.getDocId());
        if (doc == null) {
            throw new BizException("404", "文档不存在");
        }
        if (!Files.exists(Path.of(doc.getFilePath()))) {
            throw new BizException("FILE_MISSING", "源文件已被清理，无法重试");
        }
        ingestTaskDao.resetForRetry(taskId);
        docDao.resetStatus(doc.getId());
        ingestAsyncRunner.runAsync(doc.getId(), doc.getKbId(), Path.of(doc.getFilePath()), taskId);
    }

    private void validateStatus(String status) {
        if (StringUtils.hasText(status) && !TASK_STATUSES.contains(status)) {
            throw new BizException("INVALID_STATUS", "非法任务状态: " + status);
        }
    }

    /**
     * 知识库列表（含文档数），按创建时间倒序
     */
    public List<Kb> listKbs() {
        return kbDao.listAll();
    }

    /**
     * 分页查询库内文档
     */
    public PageResult<Doc> listDocs(Long kbId, int page, int pageSize) {
        Kb kb = kbDao.findById(kbId);
        if (kb == null) {
            throw new BizException("404", "知识库不存在");
        }
        long total = docDao.countByKbId(kbId);
        List<Doc> list = docDao.listByKbId(kbId, page, pageSize);
        return new PageResult<>(list, total, page, pageSize);
    }

    /**
     * 删除知识库：数据库外键级联删除 doc/chunk/ingest_task，并清理物理文件
     */
    public void deleteKb(Long kbId) {
        Kb kb = kbDao.findById(kbId);
        if (kb == null) {
            throw new BizException("404", "知识库不存在");
        }
        if (ingestTaskDao.countByKbIdAndStatus(kbId, IngestTask.STATUS_RUNNING) > 0) {
            throw new BizException("INVALID_STATE", "知识库存在进行中的入库任务，暂不能删除");
        }
        kbDao.deleteById(kbId);
        deleteStorageDir(kbId);
    }

    /**
     * 删除入库任务（仅终态可删，不影响文档）
     */
    public void deleteTask(Long taskId) {
        IngestTask task = ingestTaskDao.findById(taskId);
        if (task == null) {
            throw new BizException("404", "任务不存在");
        }
        if (IngestTask.STATUS_RUNNING.equals(task.getStatus())) {
            throw new BizException("INVALID_STATE", "任务执行中，暂不能删除");
        }
        ingestTaskDao.deleteById(taskId);
    }

    /**
     * 删除库内文档：级联删除其分块，并清理物理文件
     */
    public void deleteDoc(Long kbId, Long docId) {
        Doc doc = docDao.findById(docId);
        if (doc == null || !kbId.equals(doc.getKbId())) {
            throw new BizException("404", "文档不存在");
        }
        if (ingestTaskDao.countByDocIdAndStatus(docId, IngestTask.STATUS_RUNNING) > 0) {
            throw new BizException("INVALID_STATE", "文档存在进行中的入库任务，暂不能删除");
        }
        docDao.deleteByIdAndKbId(docId, kbId);
        deleteDocFile(doc.getFilePath());
    }

    private void deleteDocFile(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (IOException e) {
            log.warn("删除文档文件失败: {}", filePath);
        }
    }

    private void deleteStorageDir(Long kbId) {
        Path dir = Path.of(storageDir, String.valueOf(kbId));
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("删除文件失败: {}", p);
                }
            });
        } catch (IOException e) {
            log.warn("清理知识库文件目录失败 kbId={}: {}", kbId, e.getMessage());
        }
    }

    private Path saveFile(Long kbId, MultipartFile file) {
        try {
            Path dir = Path.of(storageDir, String.valueOf(kbId));
            Files.createDirectories(dir);
            String safeName = file.getOriginalFilename() == null
                    ? "doc_" + System.currentTimeMillis()
                    : file.getOriginalFilename();
            Path target = dir.resolve(System.currentTimeMillis() + "_" + safeName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new BizException("FILE_SAVE_ERROR", "文件保存失败: " + e.getMessage());
        }
    }
}
