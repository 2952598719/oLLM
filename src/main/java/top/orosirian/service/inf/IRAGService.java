package top.orosirian.service.inf;

import org.springframework.web.multipart.MultipartFile;
import top.orosirian.model.Response.TagResponseDTO;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IRAGService {

    List<TagResponseDTO> queryRagTagList(Long userId);

    void createTag(Long userId, Long tagId, String tagName);

    CompletableFuture<Boolean> uploadFile(Long userId, Long tagId, List<MultipartFile> files);

    CompletableFuture<Integer> analyzeGitRepository(Long userId, Long tagId, String repoUrl, String userName, String token) throws Exception;

}
