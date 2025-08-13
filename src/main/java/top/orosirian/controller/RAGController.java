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
import java.util.concurrent.CompletableFuture;

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
        ragService.uploadFile(userId, tagId, files);
        return ResponseEntity.accepted().body("文件上传任务已开始，正在后台处理。");
    }

    @PostMapping("/analyze_git_repository")
    public ResponseEntity<String> analyzeGitRepository(HttpSession session, @RequestParam String repoUrl, @RequestParam String userName, @RequestParam String token) throws Exception {
        Long userId = (Long) session.getAttribute(Constant.USER_SESSION_KEY);
        Long tagId = snowflake.nextId();

        CompletableFuture<Integer> future = ragService.analyzeGitRepository(userId, tagId, repoUrl, userName, token);
        future.thenAccept(result -> {
            // 这部分逻辑会在 taskExecutor 线程池中执行
            // 它可以用来记录日志、更新数据库状态、或通过WebSocket/SSE通知前端
            switch (result) {
                case 1:
                    log.info("后台任务成功：Git仓库 {} 解析并入库完毕。", repoUrl);
                    // 可以在此通过WebSocket或SSE向前端推送成功消息
                    break;
                case 2:
                    log.warn("后台任务提交失败：Git仓库 {} 的解析任务已在进行中。", repoUrl);
                    // 可以在此通过WebSocket或SSE向前端推送提示消息：“任务已在处理队列中，请勿重复提交”
                    break;
                default:
                    log.error("后台任务执行失败：Git仓库 {} 解析过程中发生错误。", repoUrl);
                    // 可以在此通过WebSocket或SSE向前端推送失败消息
                    break;
            }
        });
        ragService.analyzeGitRepository(userId, tagId, repoUrl, userName, token);
        return ResponseEntity.accepted().body("Git仓库解析任务已开始，正在后台处理。");
    }

}
