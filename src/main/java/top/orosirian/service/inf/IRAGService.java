package top.orosirian.service.inf;

import org.springframework.web.multipart.MultipartFile;
import top.orosirian.entity.Response;

import java.util.List;

public interface IRAGService {

    Response<List<String>> queryRagTagList();

    Response<String> uploadFile(String ragTag, List<MultipartFile> files);

    Response<String> analyzeGitRepository(String repoUrl, String userName, String token) throws Exception;

}
