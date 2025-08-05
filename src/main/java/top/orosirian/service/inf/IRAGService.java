package top.orosirian.service.inf;

import org.springframework.web.multipart.MultipartFile;
import top.orosirian.model.Response.TagResponseDTO;

import java.util.List;

public interface IRAGService {

    List<TagResponseDTO> queryRagTagList(Long userId);

    void createTag(Long userId, String tagName);

    boolean uploadFile(Long userId, Long tagId, List<MultipartFile> files);

    boolean analyzeGitRepository(Long userId, String repoUrl, String userName, String token) throws Exception;

}
