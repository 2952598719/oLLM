package top.orosirian.service.impl;

import cn.hutool.core.lang.Snowflake;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.PathResource;
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

@Slf4j
@Service
public class RagServiceImpl implements IRAGService {

    @Autowired
    private TokenTextSplitter tokenTextSplitter;

    @Autowired
    private PgVectorStore pgVectorStore;

    @Autowired
    private RagMapper ragMapper;

    @Override
    public List<TagResponseDTO> queryRagTagList(Long userId) {
        return ragMapper.getTagList(userId);
    }

    @Override
    public void createTag(Long userId, Long tagId, String tagName) {
        ragMapper.createTag(userId, tagId, tagName);
    }

    @Override
    public boolean uploadFile(Long userId, Long tagId, List<MultipartFile> files) {
        log.info("上传知识库开始 {}", tagId);
        for (MultipartFile file : files) {
            TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());
            List<Document> documents = documentReader.get();
            List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

            documents.forEach(doc -> doc.getMetadata().put("knowledge", String.valueOf(tagId)));
            documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", String.valueOf(tagId)));

            pgVectorStore.accept(documentSplitterList);
        }
        log.info("上传知识库结束 {}", tagId);
        return true;
    }

    @Override
    public boolean analyzeGitRepository(Long userId, Long tagId, String repoUrl, String userName, String token) throws Exception {
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
        return true;
    }

    private String extractProjectName(String repoUrl) {
        String[] parts = repoUrl.split("/");
        String projectNameWithGit = parts[parts.length - 1];
        return projectNameWithGit.replace(".git", "");
    }


    // 还有删除tag功能

}
