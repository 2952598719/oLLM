package top.orosirian.controller;

import cn.hutool.core.lang.Snowflake;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.orosirian.model.Response.TagResponseDTO;
import top.orosirian.service.inf.IRAGService;
import top.orosirian.utils.Constant;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/rag")
public class RAGController {

    @Autowired
    private IRAGService ragService;
    @Autowired
    private Snowflake snowflake;

    @GetMapping("/query_tag_list")
    public ResponseEntity<List<TagResponseDTO>> queryRagTagList(HttpSession session) {
        Long userId = (Long) session.getAttribute(Constant.USER_SESSION_KEY);
        List<TagResponseDTO> tags = ragService.queryRagTagList(userId);
        return ResponseEntity.ok(tags);
    }

    @PostMapping("/create_tag")
    public ResponseEntity<String> createTag(HttpSession session, String tagName) {
        Long userId = (Long) session.getAttribute(Constant.USER_SESSION_KEY);
        Long tagId = snowflake.nextId();
        ragService.createTag(userId, tagId, tagName);
        return ResponseEntity.ok(String.valueOf(tagId));
    }

    @PostMapping(value = "/file/upload", headers = "content-type=multipart/form-data")
    public ResponseEntity<String> uploadFile(HttpSession session, @RequestParam Long tagId, @RequestParam List<MultipartFile> files) {
        Long userId = (Long) session.getAttribute(Constant.USER_SESSION_KEY);
        if (ragService.uploadFile(userId, tagId, files)) {
            return ResponseEntity.ok("上传成功");
        } else {
            return ResponseEntity.internalServerError().body("上传失败");
        }
    }

    @PostMapping("/analyze_git_repository")
    public ResponseEntity<String> analyzeGitRepository(HttpSession session, @RequestParam String repoUrl, @RequestParam String userName, @RequestParam String token) throws Exception {
        Long userId = (Long) session.getAttribute(Constant.USER_SESSION_KEY);
        Long tagId = snowflake.nextId();
        if (ragService.analyzeGitRepository(userId, tagId, repoUrl, userName, token)) {
            return ResponseEntity.ok("解析成功");
        } else {
            return ResponseEntity.internalServerError().body("解析失败");
        }
    }

}
