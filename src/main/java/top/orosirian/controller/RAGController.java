package top.orosirian.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.orosirian.service.inf.IRAGService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/rag")
public class RAGController {

    @Autowired
    private IRAGService ragService;

    @GetMapping("/query_rag_tag_list")
    public ResponseEntity<List<String>> queryRagTagList() {
        List<String> tags = ragService.queryRagTagList();
        return ResponseEntity.ok(tags);
    }

    // http://localhost:8090/api/v1/rag/file/upload?model=deepseek-r1:1.5b&message=1+1
    @PostMapping(value = "/file/upload", headers = "content-type=multipart/form-data")
    public ResponseEntity<String> uploadFile(@RequestParam String ragTag, @RequestParam List<MultipartFile> files) {
        if (ragService.uploadFile(ragTag, files)) {
            return ResponseEntity.ok("上传成功");
        } else {
            return ResponseEntity.internalServerError().body("上传失败");
        }
    }

    @PostMapping("/analyze_git_repository")
    public ResponseEntity<String> analyzeGitRepository(@RequestParam String repoUrl, @RequestParam String userName, @RequestParam String token) throws Exception {
        if (ragService.analyzeGitRepository(repoUrl, userName, token)) {
            return ResponseEntity.ok("解析成功");
        } else {
            return ResponseEntity.internalServerError().body("解析失败");
        }
    }

}
