package com.ragbase.knowledge.controller;

import com.ragbase.common.web.PageResult;
import com.ragbase.common.web.Result;
import com.ragbase.knowledge.domain.Doc;
import com.ragbase.knowledge.domain.IngestTask;
import com.ragbase.knowledge.domain.Kb;
import com.ragbase.knowledge.service.KnowledgeService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理 API
 */
@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KbController {

    private final KnowledgeService knowledgeService;

    /**
     * 创建知识库
     */
    @PostMapping
    public Result<Map<String, Long>> createKb(@RequestParam @NotBlank String name,
                                              @RequestParam(required = false) String description) {
        Long kbId = knowledgeService.createKb(name, description);
        return Result.success(Map.of("kbId", kbId));
    }

    /**
     * 知识库列表（含文档数）
     */
    @GetMapping
    public Result<List<Kb>> listKbs() {
        return Result.success(knowledgeService.listKbs());
    }

    /**
     * 删除知识库（级联删除库内文档与分块）
     */
    @DeleteMapping("/{kbId}")
    public Result<Void> deleteKb(@PathVariable Long kbId) {
        knowledgeService.deleteKb(kbId);
        return Result.success();
    }

    /**
     * 分页查询库内文档
     */
    @GetMapping("/{kbId}/docs")
    public Result<PageResult<Doc>> listDocs(@PathVariable Long kbId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(knowledgeService.listDocs(kbId, page, pageSize));
    }

    /**
     * 上传文档（异步入库），返回任务 id
     */
    @PostMapping(value = "/{kbId}/docs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Long>> uploadDoc(@PathVariable Long kbId,
                                               @RequestPart MultipartFile file) {
        Long taskId = knowledgeService.uploadDocument(kbId, file);
        return Result.success(Map.of("taskId", taskId));
    }

    /**
     * 查询入库任务状态
     */
    @GetMapping("/tasks/{taskId}")
    public Result<IngestTask> getTask(@PathVariable Long taskId) {
        return Result.success(knowledgeService.getTask(taskId));
    }

    /**
     * 分页查询入库任务，可按状态过滤
     */
    @GetMapping("/tasks")
    public Result<PageResult<IngestTask>> listTasks(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int pageSize,
                                                    @RequestParam(required = false) String status) {
        return Result.success(knowledgeService.listTasks(page, pageSize, status));
    }

    /**
     * 重试失败入库任务
     */
    @PostMapping("/tasks/{taskId}/retry")
    public Result<Void> retryTask(@PathVariable Long taskId) {
        knowledgeService.retryTask(taskId);
        return Result.success();
    }

    /**
     * 删除入库任务（仅终态）
     */
    @DeleteMapping("/tasks/{taskId}")
    public Result<Void> deleteTask(@PathVariable Long taskId) {
        knowledgeService.deleteTask(taskId);
        return Result.success();
    }

    /**
     * 删除库内文档（级联删除分块）
     */
    @DeleteMapping("/{kbId}/docs/{docId}")
    public Result<Void> deleteDoc(@PathVariable Long kbId, @PathVariable Long docId) {
        knowledgeService.deleteDoc(kbId, docId);
        return Result.success();
    }
}
