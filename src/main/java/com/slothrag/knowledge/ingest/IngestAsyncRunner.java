package com.slothrag.knowledge.ingest;

import com.slothrag.knowledge.dao.IngestTaskDao;
import com.slothrag.knowledge.domain.IngestTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 独立异步 Bean：承载 @Async 入库调用。
 * <p>
 * 必须单独抽取为独立 Bean，否则 KnowledgeService 类内调用 @Async 方法
 * 不会经过 Spring AOP 代理，导致同步阻塞（详见 {@code @Async} 自调用陷阱）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestAsyncRunner {

    private final IngestPipeline ingestPipeline;
    private final IngestTaskDao ingestTaskDao;

    @Async
    public void runAsync(Long docId, Long kbId, Path file, Long taskId) {
        ingestTaskDao.updateStatus(taskId, IngestTask.STATUS_RUNNING, null);
        ingestPipeline.execute(docId, kbId, file, taskId);
    }
}
