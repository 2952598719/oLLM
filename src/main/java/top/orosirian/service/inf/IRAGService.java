package top.orosirian.service.inf;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IRAGService {

    List<String> queryRagTagList();

    boolean uploadFile(String ragTag, List<MultipartFile> files);

    boolean analyzeGitRepository(String repoUrl, String userName, String token) throws Exception;

}
