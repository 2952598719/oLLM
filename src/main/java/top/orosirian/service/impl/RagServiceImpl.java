package top.orosirian.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.redisson.api.RLockReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.PathResource;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.orosirian.mapper.RagMapper;
import top.orosirian.model.Response.TagResponseDTO;
import top.orosirian.service.inf.IRAGService;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
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

    @Qualifier("redissonReactiveClient")
    @Autowired
    private RedissonReactiveClient redissonReactiveClient;

    @Override
    public Flux<TagResponseDTO> queryRagTagList(Long userId) {
        return Mono.fromCallable(() -> ragMapper.getTagList(userId)) // 1. 将阻塞调用包装在 Callable 中
                .flatMapMany(Flux::fromIterable) // 2. 将 List<T> 转换为 Flux<T>
                .publishOn(Schedulers.boundedElastic()); // 3. 指定在专门的线程池上执行阻塞调用
    }

    @Override
    public Mono<Void> createTag(Long userId, Long tagId, String tagName) {
        return Mono.fromRunnable(() -> ragMapper.createTag(userId, tagId, tagName)) // 1. 对于没有返回值的方法，使用 fromRunnable
                .publishOn(Schedulers.boundedElastic()) // 2. 在专门的线程池上执行
                .then(); // 3. 转换为 Mono<Void>
    }

    @Override
    public Mono<Boolean> uploadFile(Long userId, Long tagId, Flux<FilePart> fileParts) {
        // fileParts.flatMap(...) 会对流中的每个文件执行操作
        return fileParts
                .flatMap(filePart -> saveAndProcessFile(filePart, tagId)) // 对每个文件进行保存和处理
                .collectList() // 等待所有文件处理完成
                .then(Mono.just(true)) // 所有文件成功后返回 true
                .doOnError(e -> log.error("文件上传处理流中发生错误 for tagId: {}", tagId, e))
                .onErrorReturn(false); // 任何一个文件失败则整个操作返回 false
    }
    private Mono<Void> saveAndProcessFile(FilePart filePart, Long tagId) {
        // 由于Tika需要一个File/Resource，我们先将FilePart写入临时文件
        // 这是处理响应式上传和阻塞库集成的常见模式
        Path tempFile;
        try {
            tempFile = Files.createTempFile("upload-", "-" + filePart.filename());
        } catch (IOException e) {
            return Mono.error(e);
        }

        // 1. 异步地将文件内容传输到临时文件
        return filePart.transferTo(tempFile)
                .then(Mono.fromRunnable(() -> {
                    // 2. 在 transferTo 完成后，在专用线程池上执行阻塞的处理逻辑
                    try {
                        log.info("开始处理文件: {}", filePart.filename());
                        TikaDocumentReader documentReader = new TikaDocumentReader(new PathResource(tempFile));
                        List<Document> documents = documentReader.get();
                        List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

                        documents.forEach(doc -> doc.getMetadata().put("knowledge", String.valueOf(tagId)));
                        documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", String.valueOf(tagId)));

                        pgVectorStore.accept(documentSplitterList);
                        log.info("文件处理完成: {}", filePart.filename());
                    } catch (Exception e) {
                        // 在响应式流中，通过抛出异常来表示错误
                        throw new RuntimeException("处理文件失败: " + filePart.filename(), e);
                    } finally {
                        // 3. 确保临时文件被删除
                        try {
                            Files.deleteIfExists(tempFile);
                        } catch (IOException e) {
                            log.warn("删除临时文件失败: {}", tempFile, e);
                        }
                    }
                }).publishOn(Schedulers.boundedElastic()))
                .then(); // 转换为 Mono<Void>
    }

    @Override
    public Mono<Integer> analyzeGitRepository(Long userId, Long tagId, String repoUrl, String userName, String token) {
        String lockKey = "lock:git_analyze:" + repoUrl.hashCode();
        RLockReactive lock = redissonReactiveClient.getLock(lockKey);

        // 将核心的、耗时的阻塞业务逻辑封装在一个 Mono<Integer> 中。
        // 这个 Mono 只有在锁被获取后才会执行。
        Mono<Integer> mainTaskLogic = Mono.fromCallable(() -> {
            try {
                log.info("成功获取Git仓库解析锁，开始执行任务。Repo: {}", repoUrl);
                String repoProjectName = extractProjectName(repoUrl);
                String localPath = String.format("./additional/repos/%s/%s", userName, repoProjectName);
                log.info("克隆路径: {}", new File(localPath).getAbsolutePath());

                // 包含所有文件IO、Git操作和数据库IO的阻塞代码块
                FileUtils.deleteDirectory(new File(localPath));
                try (Git ignored = Git.cloneRepository()
                        .setURI(repoUrl)
                        .setDirectory(new File(localPath))
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(userName, token))
                        .call()) {
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
                }
                // 确保 Git 对象被关闭
                FileUtils.deleteDirectory(new File(localPath));
                ragMapper.createTag(userId, tagId, repoProjectName);

                log.info("遍历解析路径，上传完成: {}", repoUrl);
                return 1; // 成功
            } catch (Exception e) {
                log.error("异步Git解析失败。Repo: {}", repoUrl, e);
                return 4; // 其他异常
            }
        }).publishOn(Schedulers.boundedElastic()); // 确保这个阻塞任务在专用线程池上执行
        // 构建完整的响应式链：尝试获取锁 -> 执行任务 -> 释放锁
        return lock.tryLock(0, 600, TimeUnit.SECONDS) // 1. 异步尝试获取锁，返回 Mono<Boolean>
                .flatMap(isLocked -> {
                    if (!isLocked) {
                        // 2. 如果没有获取到锁，直接返回结果码 2
                        log.warn("获取Git仓库解析锁失败，已有任务正在执行。Repo: {}", repoUrl);
                        return Mono.just(2);
                    }

                    // 3. 如果获取到锁，执行主任务。
                    //    并使用 doFinally 来确保锁在任何情况下都会被释放。
                    return mainTaskLogic
                            .doFinally(signalType -> {
                                log.info("Git仓库解析任务执行完毕或出错，释放锁。Signal: {}. Repo: {}", signalType, repoUrl);
                                // 4. 异步释放锁。这是一个"触发并忘记"的操作，
                                //    我们必须 .subscribe() 才能确保释放命令被发送。
                                lock.unlock().subscribe();
                            });
                });
    }

    private String extractProjectName(String repoUrl) {
        String[] parts = repoUrl.split("/");
        String projectNameWithGit = parts[parts.length - 1];
        return projectNameWithGit.replace(".git", "");
    }

}
