package com.ragbase.knowledge.ingest;

import com.ragbase.ai.embedding.EmbeddingClient;
import com.ragbase.knowledge.chunk.ChunkStrategy;
import com.ragbase.knowledge.dao.ChunkDao;
import com.ragbase.knowledge.dao.DocDao;
import com.ragbase.knowledge.dao.IngestTaskDao;
import com.ragbase.knowledge.domain.Chunk;
import com.ragbase.knowledge.domain.Doc;
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

            // 1. 解析
            String text = documentParser.parse(file);
            log.info("文档解析完成 docId={}, 文本长度={}", docId, text.length());

            // 2. 分块
            docDao.updateStatus(docId, Doc.STATUS_CHUNKING);
            ingestTaskDao.updateProgress(taskId, 40, "CHUNK");
            List<String> chunks = chunkStrategy.split(text);
            if (chunks.isEmpty()) {
                throw new RuntimeException("文档解析后无可分块内容");
            }
            log.info("分块完成 docId={}, 块数={}", docId, chunks.size());

            // 3. Embedding
            ingestTaskDao.updateProgress(taskId, 60, "EMBED");
            List<float[]> vectors = embeddingClient.embed(chunks);
            if (vectors.size() != chunks.size()) {
                throw new RuntimeException("Embedding 结果数量与分块数不一致");
            }

            // 4. 批量写库
            ingestTaskDao.updateProgress(taskId, 80, "INDEX");
            List<Chunk> chunkEntities = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                Chunk c = new Chunk();
                c.setDocId(docId);
                c.setKbId(kbId);
                c.setSeq(i);
                c.setContent(chunks.get(i));
                c.setVector(vectors.get(i));
                chunkEntities.add(c);
            }
            chunkDao.batchInsert(chunkEntities);

            // 5. 收尾
            docDao.updateChunkCount(docId, chunks.size());
            ingestTaskDao.updateStatus(taskId, IngestTaskStatus.SUCCESS, null);
            log.info("入库完成 docId={}, 入库块数={}", docId, chunks.size());
        } catch (Exception e) {
            log.error("入库失败 docId={}", docId, e);
            docDao.markFailed(docId, e.getMessage());
            ingestTaskDao.updateStatus(taskId, IngestTaskStatus.FAILED, e.getMessage());
        }
    }

    private static final class IngestTaskStatus {
        static final String SUCCESS = "SUCCESS";
        static final String FAILED = "FAILED";
    }
}
