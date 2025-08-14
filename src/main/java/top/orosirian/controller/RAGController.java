package top.orosirian.controller;

import cn.hutool.core.lang.Snowflake;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.orosirian.model.Response.TagResponseDTO;
import top.orosirian.model.request.GitRequest;
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
    public Mono<ResponseEntity<List<TagResponseDTO>>> queryRagTagList(ServerWebExchange exchange) {
        return exchange.getSession()
                .mapNotNull(webSession -> (Long) webSession.getAttribute(Constant.USER_SESSION_KEY))
                .flatMap(userId -> {
                    // 假设 ragService.queryRagTagList 现在返回 Flux<TagResponseDTO>
                    Flux<TagResponseDTO> tagsFlux = ragService.queryRagTagList(userId);
                    return tagsFlux.collectList()
                            .map(ResponseEntity::ok);
                });
    }

    @PostMapping("/create_tag")
    public Mono<ResponseEntity<String>> createTag(ServerWebExchange exchange, @RequestParam String tagName) {
        return exchange.getSession()
                .mapNotNull(webSession -> (Long) webSession.getAttribute(Constant.USER_SESSION_KEY))
                .flatMap(userId -> {
                    Long tagId = snowflake.nextId();
                    // 假设 ragService.createTag 是一个异步操作，返回 Mono<Void>
                    return ragService.createTag(userId, tagId, tagName)
                            .then(Mono.just(ResponseEntity.ok(String.valueOf(tagId))));
                });
    }

    @PostMapping(value = "/file/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<String>> uploadFile(ServerWebExchange exchange,
            @RequestParam Long tagId,
            @RequestPart("files") Flux<FilePart> fileParts) {
        return exchange.getSession()
                .mapNotNull(webSession -> (Long) webSession.getAttribute(Constant.USER_SESSION_KEY))
                .flatMap(userId -> {
                    // 假设 ragService.uploadFile 现在处理响应式的 Flux<FilePart>
                    return ragService.uploadFile(userId, tagId, fileParts)
                            .then(Mono.just(ResponseEntity.accepted().body("文件上传任务已开始，正在后台处理。")));
                });
    }

    @PostMapping("/analyze_git_repository")
    public Mono<ResponseEntity<String>> analyzeGitRepository(ServerWebExchange exchange,
            @RequestBody @Valid Mono<GitRequest> requestMono) {
        return requestMono.flatMap(request ->
                exchange.getSession()
                        .mapNotNull(webSession -> (Long) webSession.getAttribute(Constant.USER_SESSION_KEY))
                        .flatMap(userId -> {
                            Long tagId = snowflake.nextId();

                            // 将 CompletableFuture 转换为 Mono
                            Mono<Integer> futureMono = ragService.analyzeGitRepository(userId, tagId, request.getRepoUrl(), request.getUserName(), request.getToken());
                            futureMono.doOnSuccess(result -> {
                                // 这部分逻辑会在任务成功完成时执行
                                switch (result) {
                                    case 1:
                                        log.info("后台任务成功：Git仓库 {} 解析并入库完毕。", request.getRepoUrl());
                                        break;
                                    case 2:
                                        log.warn("后台任务提交失败：Git仓库 {} 的解析任务已在进行中。", request.getRepoUrl());
                                        break;
                                    default:
                                        log.error("后台任务执行失败：Git仓库 {} 解析过程中发生错误。", request.getRepoUrl());
                                        break;
                                }
                            }).subscribe(); // 触发执行

                            return Mono.just(ResponseEntity.accepted().body("Git仓库解析任务已开始，正在后台处理。"));
                        })
        );
    }

}
