package top.orosirian.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.orosirian.entity.Response;
import top.orosirian.service.inf.IRAGService;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/rag")
public class RAGController {

    @Autowired
    private IRAGService ragService;

    @GetMapping("/query_rag_tag_list")
    public Response<List<String>> queryRagTagList() {
        return ragService.queryRagTagList();
    }

    // http://localhost:8090/api/v1/rag/file/upload?model=deepseek-r1:1.5b&message=1+1
    @PostMapping(value = "/file/upload", headers = "content-type=multipart/form-data")
    public Response<String> uploadFile(@RequestParam String ragTag, @RequestParam List<MultipartFile> files) {
        return ragService.uploadFile(ragTag, files);
    }

    @PostMapping("/analyze_git_repository")
    public Response<String> analyzeGitRepository(@RequestParam String repoUrl, @RequestParam String userName, @RequestParam String token) throws Exception {
        return ragService.analyzeGitRepository(repoUrl, userName, token);
    }

}
