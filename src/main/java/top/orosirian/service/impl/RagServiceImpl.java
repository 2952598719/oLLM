package top.orosirian.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.PathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.orosirian.mapper.RagMapper;
import top.orosirian.model.Response.TagResponseDTO;
import top.orosirian.service.inf.IRAGService;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RagServiceImpl implements IRAGService {

    @Autowired
    private TokenTextSplitter tokenTextSplitter;

    @Autowired
    private PgVectorStore pgVectorStore;

    @Autowired
    private RagMapper ragMapper;
    @Qualifier("redissonClient")
    @Autowired
    private RedissonClient redissonClient;

    @Override
    public List<TagResponseDTO> queryRagTagList(Long userId) {
        return ragMapper.getTagList(userId);
    }

    @Override
    public void createTag(Long userId, Long tagId, String tagName) {
        ragMapper.createTag(userId, tagId, tagName);
    }

    @Async("taskExecutor")  // 新开一个线程执行
    @Override
    public CompletableFuture<Boolean> uploadFile(Long userId, Long tagId, List<MultipartFile> files) {
        try {
            log.info("异步上传知识库开始 {}", tagId);
            for (MultipartFile file : files) {
                TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());
                List<Document> documents = documentReader.get();
                List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

                documents.forEach(doc -> doc.getMetadata().put("knowledge", String.valueOf(tagId)));
                documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", String.valueOf(tagId)));

                pgVectorStore.accept(documentSplitterList);
            }
            log.info("异步上传知识库结束 {}", tagId);
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("异步上传文件失败 for tagId: {}", tagId, e);
            return CompletableFuture.completedFuture(false);
        }
    }

    @Async("taskExecutor")
    @Override
    public CompletableFuture<Integer> analyzeGitRepository(Long userId, Long tagId, String repoUrl, String userName, String token) throws Exception {
        // 1. 定义锁的唯一Key，通常使用一个固定的前缀+业务唯一标识
        String lockKey = "lock:git_analyze:" + repoUrl.hashCode(); // 使用hashCode减少key的长度
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean isLocked = lock.tryLock(0, 600, TimeUnit.SECONDS);
            if (!isLocked) {
                log.warn("获取Git仓库解析锁失败，已有任务正在执行。Repo: {}", repoUrl);
                // 未获取到锁，说明有其他线程正在处理，直接返回任务失败
                return CompletableFuture.completedFuture(2);
            }

            log.info("成功获取Git仓库解析锁，开始执行任务。Repo: {}", repoUrl);
            String repoProjectName = extractProjectName(repoUrl);
            String localPath = String.format("./additional/repos/%s/%s", userName, repoProjectName);
            log.info("克隆路径: {}", new File(localPath).getAbsolutePath());

            FileUtils.deleteDirectory(new File(localPath));
            Git git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(new File(localPath))
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(userName, token))
                    .call();
            Files.walkFileTree(Paths.get(localPath), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    log.info("{} 遍历解析路径，上传知识库: {}", repoProjectName, file.getFileName());
                    try {
                        TikaDocumentReader reader = new TikaDocumentReader(new PathResource(file));
                        List<Document> documents = reader.get();
                        List<Document> documentSplitterList = tokenTextSplitter.apply(documents);
                        documents.forEach(doc -> doc.getMetadata().put("knowledge", String.valueOf(tagId)));
                        documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", String.valueOf(tagId)));
                        pgVectorStore.accept(documentSplitterList);
                    } catch (Exception e) {
                        log.error("遍历解析路径，上传知识库失败: {}", file.getFileName());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // 当目录是.git时，跳过整个子树
                    if (".git".equals(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.error("Failed to access file: {} - {}", file.toString(), exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
            git.close();

            FileUtils.deleteDirectory(new File(localPath));

            ragMapper.createTag(userId, tagId, repoProjectName);

            log.info("遍历解析路径，上传完成: {}", repoUrl);
            return CompletableFuture.completedFuture(1);
        } catch (InterruptedException e) {
            // 当前线程在等待获取锁时被中断
            Thread.currentThread().interrupt();
            log.error("等待Git仓库解析锁时被中断。Repo: {}", repoUrl, e);
            return CompletableFuture.completedFuture(3);
        } catch (Exception e) {
            log.error("异步Git解析失败。Repo: {}", repoUrl, e);
            return CompletableFuture.completedFuture(4);
        } finally {
            // 4. 释放锁。必须在finally块中执行，确保任何情况下都能释放锁。
            if (lock.isHeldByCurrentThread()) { // 检查当前线程是否还持有锁
                lock.unlock();
                log.info("Git仓库解析任务执行完毕，释放锁。Repo: {}", repoUrl);
            }
        }
    }

    private String extractProjectName(String repoUrl) {
        String[] parts = repoUrl.split("/");
        String projectNameWithGit = parts[parts.length - 1];
        return projectNameWithGit.replace(".git", "");
    }

}
