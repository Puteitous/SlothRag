package com.slothrag.knowledge.ingest;

import com.slothrag.ai.embedding.EmbeddingClient;
import com.slothrag.knowledge.chunk.ChunkDraft;
import com.slothrag.knowledge.chunk.ChunkStrategy;
import com.slothrag.knowledge.dao.ChunkDao;
import com.slothrag.knowledge.dao.DocDao;
import com.slothrag.knowledge.dao.IngestTaskDao;
import com.slothrag.knowledge.domain.Chunk;
import com.slothrag.knowledge.domain.Doc;
import com.slothrag.knowledge.domain.IngestTask;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 入库 Pipeline：解析 → 分块 → embedding → 批量写库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestPipeline {

    private final DocumentParser documentParser;
    private final ChunkStrategy chunkStrategy;
    private final EmbeddingClient embeddingClient;
    private final DocDao docDao;
    private final ChunkDao chunkDao;
    private final IngestTaskDao ingestTaskDao;

    /**
     * 执行入库流程（在异步线程中调用）
     *
     * @param docId  doc 记录 id
     * @param kbId   知识库 id
     * @param file   文档文件
     * @param taskId 入库任务 id
     */
    public void execute(Long docId, Long kbId, Path file, Long taskId) {
        try {
            docDao.updateStatus(docId, Doc.STATUS_PARSING);
            ingestTaskDao.updateProgress(taskId, 10, "PARSE");

            // 1. 解析为结构块（Block-Aware）
            var blocks = documentParser.parseToBlocks(file);
            log.info("文档解析完成 docId={}, block数={}", docId, blocks.size());

            // 2. Block-Aware 分块
            docDao.updateStatus(docId, Doc.STATUS_CHUNKING);
            ingestTaskDao.updateProgress(taskId, 40, "CHUNK");
            List<ChunkDraft> drafts = chunkStrategy.splitToDrafts(blocks, kbId);
            if (drafts.isEmpty()) {
                throw new RuntimeException("文档解析后无可分块内容");
            }
            log.info("分块完成 docId={}, 块数={}", docId, drafts.size());

            // 3. Embedding
            ingestTaskDao.updateProgress(taskId, 60, "EMBED");
            List<String> contents = drafts.stream().map(ChunkDraft::content).toList();
            List<float[]> vectors = embeddingClient.embed(contents);
            if (vectors.size() != drafts.size()) {
                throw new RuntimeException("Embedding 结果数量与分块数不一致");
            }

            // 4. 批量写库（含 heading_path）
            ingestTaskDao.updateProgress(taskId, 80, "INDEX");
            List<Chunk> chunkEntities = new ArrayList<>(drafts.size());
            for (int i = 0; i < drafts.size(); i++) {
                ChunkDraft draft = drafts.get(i);
                Chunk c = new Chunk();
                c.setDocId(docId);
                c.setKbId(kbId);
                c.setSeq(i);
                c.setContent(draft.content());
                c.setHeadingPath(draft.headingPath());
                c.setVector(vectors.get(i));
                chunkEntities.add(c);
            }
            chunkDao.batchInsert(chunkEntities);

            // 5. 收尾
            docDao.updateChunkCount(docId, drafts.size());
            ingestTaskDao.updateStatus(taskId, IngestTask.STATUS_SUCCESS, null);
            log.info("入库完成 docId={}, 入库块数={}", docId, drafts.size());
        } catch (Exception e) {
            log.error("入库失败 docId={}", docId, e);
            docDao.markFailed(docId, e.getMessage());
            ingestTaskDao.updateStatus(taskId, IngestTask.STATUS_FAILED, e.getMessage());
        }
    }
}
